package com.ssolab.auth.oidc.logout;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OidcClientSessionRepository extends JpaRepository<OidcClientSessionEntity, UUID> {
    Optional<OidcClientSessionEntity> findByAuthSessionIdAndRegisteredClientId(
        String authSessionId, String registeredClientId);
    List<OidcClientSessionEntity> findByAuthSessionId(String authSessionId);
    List<OidcClientSessionEntity> findByUserId(UUID userId);
    List<OidcClientSessionEntity> findByUserIdAndOidcSid(UUID userId, String oidcSid);
}
