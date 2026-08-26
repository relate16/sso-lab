package com.ssolab.auth.oidc.logout;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LogoutCoordinator {
    private final LogoutStateService state;
    private final BackChannelLogoutDispatcher dispatcher;
    private final JdbcTemplate jdbc;

    public LogoutCoordinator(LogoutStateService state,
        BackChannelLogoutDispatcher dispatcher, JdbcTemplate jdbc) {
        this.state = state; this.dispatcher = dispatcher; this.jdbc = jdbc;
    }

    public void logoutOne(UUID userId, String authSessionId, String currentSessionId) {
        complete(state.revokeOne(userId, authSessionId), currentSessionId);
    }

    public void logoutByPublicId(UUID userId, UUID publicId, String currentSessionId) {
        complete(state.revokeByPublicId(userId, publicId), currentSessionId);
    }

    public void logoutByOidcSid(UUID userId, String oidcSid) {
        complete(state.revokeByOidcSid(userId, oidcSid), null);
    }

    public void logoutAll(UUID userId, String currentSessionId) {
        complete(state.revokeAll(userId), currentSessionId);
    }

    private void complete(LogoutBatch batch, String currentSessionId) {
        batch.authSessionIds().stream()
            .filter(id -> currentSessionId == null || !currentSessionId.equals(id))
            .forEach(id -> jdbc.update("DELETE FROM auth.SPRING_SESSION WHERE SESSION_ID=?", id));
        dispatcher.dispatch(batch.commands());
    }
}
