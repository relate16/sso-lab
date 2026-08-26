package com.ssolab.auth.oidc.logout;

import com.ssolab.auth.passwordless.session.PasswordlessPrincipal;
import java.time.Clock;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OidcClientSessionService {
    private final OidcClientSessionRepository repository;
    private final Clock clock;

    public OidcClientSessionService(OidcClientSessionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void link(PasswordlessPrincipal principal, RegisteredClient client,
        OAuth2Authorization authorization) {
        String uri = client.getClientSettings().getSetting("sso.back-channel-logout-uri");
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException("registered client has no back-channel logout URI");
        }
        String authorizationId = authorization == null ? null : authorization.getId();
        var existing = repository.findByAuthSessionIdAndRegisteredClientId(
            principal.sessionId(), client.getId());
        if (existing.isPresent()) {
            existing.get().refresh(authorizationId, clock.instant());
            repository.saveAndFlush(existing.get());
            return;
        }
        repository.saveAndFlush(OidcClientSessionEntity.create(
            principal.sessionId(), principal.userId(), authorizationId, client.getId(),
            client.getClientId(), OidcSessionIdHasher.hash(principal.sessionId()), uri,
            clock.instant()));
    }
}
