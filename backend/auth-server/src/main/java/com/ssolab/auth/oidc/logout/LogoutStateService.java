package com.ssolab.auth.oidc.logout;

import com.ssolab.auth.admin.bootstrap.BootstrapAdminStateRepository;
import com.ssolab.auth.audit.AuditEvent;
import com.ssolab.auth.audit.AuditService;
import com.ssolab.auth.audit.AuditSource;
import com.ssolab.auth.identity.model.AccountStatus;
import com.ssolab.auth.identity.model.RoleName;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.identity.service.IdentityConflictException;
import com.ssolab.auth.identity.service.IdentityNotFoundException;
import com.ssolab.auth.passwordless.otp.PendingRegistrationRepository;
import com.ssolab.auth.passwordless.session.UserSessionMetadataRepository;
import java.net.URI;
import java.security.SecureRandom;
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
    private final UserIdentityRepository users;
    private final PendingRegistrationRepository pendingRegistrations;
    private final BootstrapAdminStateRepository bootstrapState;
    private final AuditService audit;
    private final Clock clock;

    LogoutStateService(UserSessionMetadataRepository sessions,
        OidcClientSessionRepository clientSessions,
        JdbcTemplate jdbc,
        UserIdentityRepository users,
        PendingRegistrationRepository pendingRegistrations,
        BootstrapAdminStateRepository bootstrapState,
        AuditService audit,
        Clock clock) {
        this.sessions = sessions; this.clientSessions = clientSessions;
        this.jdbc = jdbc; this.users = users;
        this.pendingRegistrations = pendingRegistrations;
        this.bootstrapState = bootstrapState;
        this.audit = audit;
        this.clock = clock;
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

    @Transactional
    LogoutBatch revokeForEmailChange(UUID userId, String currentSessionId) {
        var otherSessions = sessions.findByUser_IdAndInvalidatedAtIsNull(userId).stream()
            .filter(value -> !value.getSessionId().equals(currentSessionId))
            .toList();
        otherSessions.forEach(value -> value.invalidate(clock.instant()));
        sessions.saveAllAndFlush(otherSessions);
        List<OidcClientSessionEntity> links = clientSessions.findByUserId(userId);
        jdbc.update("DELETE FROM auth.oauth2_authorization WHERE principal_name=?",
            userId.toString());
        clientSessions.deleteAllInBatch(links);
        return batch(
            otherSessions.stream().map(value -> value.getSessionId()).toList(),
            links,
            userId
        );
    }

    @Transactional
    LogoutBatch hardDelete(UUID userId, String traceId) {
        var user = users.findLockedById(userId)
            .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
            .orElseThrow(() -> new IdentityNotFoundException("identity is unavailable"));
        if (user.hasRole(RoleName.ADMIN)
            && users.countByRoleAndStatus(RoleName.ADMIN, AccountStatus.ACTIVE) <= 1) {
            throw new IdentityConflictException("the last active ADMIN cannot be deleted");
        }

        var ownedSessions = sessions.findByUser_Id(userId);
        List<OidcClientSessionEntity> links = clientSessions.findByUserId(userId);
        LogoutBatch batch = batch(
            ownedSessions.stream().map(value -> value.getSessionId()).toList(),
            links,
            userId
        );

        jdbc.update("DELETE FROM auth.oauth2_authorization WHERE principal_name=?",
            userId.toString());
        jdbc.update("DELETE FROM auth.oauth2_authorization_consent WHERE principal_name=?",
            userId.toString());
        pendingRegistrations.deletePersonalData(
            user.getNormalizedUserId(), user.getEmailLookupHash()
        );

        bootstrapState.findLocked()
            .filter(state -> userId.equals(state.getClaimedBy()))
            .ifPresent(state -> {
                byte[] tombstone = new byte[32];
                new SecureRandom().nextBytes(tombstone);
                try {
                    state.unlinkDeletedClaim(tombstone, clock.instant());
                    bootstrapState.saveAndFlush(state);
                } finally {
                    java.util.Arrays.fill(tombstone, (byte) 0);
                }
            });

        audit.recordWithinTransaction(
            AuditEvent.ACCOUNT_DELETED,
            userId,
            userId,
            true,
            AuditSource.AUTH_WEB,
            traceId
        );
        jdbc.update("DELETE FROM auth.oidc_client_sessions WHERE user_id=?", userId);
        jdbc.update("DELETE FROM auth.user_session_metadata WHERE user_id=?", userId);
        int deleted = jdbc.update("DELETE FROM auth.users WHERE id=?", userId);
        if (deleted != 1) {
            throw new IdentityNotFoundException("identity is unavailable");
        }
        return batch;
    }

    private LogoutBatch batch(List<String> ids, List<OidcClientSessionEntity> links, UUID userId) {
        List<BackChannelLogoutCommand> commands = links.stream().map(link ->
            new BackChannelLogoutCommand(link.getClientId(),
                URI.create(link.getBackChannelLogoutUri()), userId.toString(), link.getOidcSid()))
            .toList();
        return new LogoutBatch(ids, commands);
    }
}
