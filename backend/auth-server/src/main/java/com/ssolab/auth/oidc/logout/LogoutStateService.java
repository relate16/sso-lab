package com.ssolab.auth.oidc.logout;

import com.ssolab.auth.passwordless.session.UserSessionMetadataRepository;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LogoutStateService {
    private final UserSessionMetadataRepository sessions;
    private final OidcClientSessionRepository clientSessions;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    LogoutStateService(UserSessionMetadataRepository sessions,
        OidcClientSessionRepository clientSessions, JdbcTemplate jdbc, Clock clock) {
        this.sessions = sessions; this.clientSessions = clientSessions;
        this.jdbc = jdbc; this.clock = clock;
    }

    @Transactional
    LogoutBatch revokeOne(UUID userId, String authSessionId) {
        var session = sessions.findLockedBySessionId(authSessionId)
            .filter(value -> value.getUser().getId().equals(userId))
            .orElseThrow(() -> new IllegalArgumentException("session is unavailable"));
        if (session.getInvalidatedAt() == null) session.invalidate(clock.instant());
        sessions.saveAndFlush(session);
        List<OidcClientSessionEntity> links = clientSessions.findByAuthSessionId(authSessionId);
        links.stream().map(OidcClientSessionEntity::getAuthorizationId)
            .filter(id -> id != null && !id.isBlank()).distinct()
            .forEach(id -> jdbc.update("DELETE FROM auth.oauth2_authorization WHERE id=?", id));
        clientSessions.deleteAllInBatch(links);
        return batch(List.of(authSessionId), links, userId);
    }

    @Transactional
    LogoutBatch revokeByPublicId(UUID userId, UUID publicId) {
        String sessionId = sessions.findLockedByPublicIdAndUser_Id(publicId, userId)
            .orElseThrow(() -> new IllegalArgumentException("session is unavailable"))
            .getSessionId();
        return revokeOne(userId, sessionId);
    }

    @Transactional
    LogoutBatch revokeByOidcSid(UUID userId, String oidcSid) {
        String sessionId = clientSessions.findByUserIdAndOidcSid(userId, oidcSid).stream()
            .map(OidcClientSessionEntity::getAuthSessionId).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("session is unavailable"));
        return revokeOne(userId, sessionId);
    }

    @Transactional
    LogoutBatch revokeAll(UUID userId) {
        var active = sessions.findByUser_IdAndInvalidatedAtIsNull(userId);
        active.forEach(value -> value.invalidate(clock.instant()));
        sessions.saveAllAndFlush(active);
        List<OidcClientSessionEntity> links = clientSessions.findByUserId(userId);
        jdbc.update("DELETE FROM auth.oauth2_authorization WHERE principal_name=?",
            userId.toString());
        clientSessions.deleteAllInBatch(links);
        return batch(active.stream().map(value -> value.getSessionId()).toList(), links, userId);
    }

    private LogoutBatch batch(List<String> ids, List<OidcClientSessionEntity> links, UUID userId) {
        List<BackChannelLogoutCommand> commands = links.stream().map(link ->
            new BackChannelLogoutCommand(link.getClientId(),
                URI.create(link.getBackChannelLogoutUri()), userId.toString(), link.getOidcSid()))
            .toList();
        return new LogoutBatch(ids, commands);
    }
}
