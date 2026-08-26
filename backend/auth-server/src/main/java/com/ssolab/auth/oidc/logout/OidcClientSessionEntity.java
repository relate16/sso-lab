package com.ssolab.auth.oidc.logout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "OidcClientSession")
@Table(name = "oidc_client_sessions", schema = "auth")
public class OidcClientSessionEntity {
    @Id private UUID id;
    @Column(name = "auth_session_id", nullable = false, length = 128) private String authSessionId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "authorization_id", length = 100) private String authorizationId;
    @Column(name = "registered_client_id", nullable = false, length = 100) private String registeredClientId;
    @Column(name = "client_id", nullable = false, length = 100) private String clientId;
    @Column(name = "oidc_sid", nullable = false, length = 64) private String oidcSid;
    @Column(name = "backchannel_logout_uri", nullable = false, length = 500) private String backChannelLogoutUri;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "last_accessed_at", nullable = false) private Instant lastAccessedAt;

    protected OidcClientSessionEntity() {
    }

    static OidcClientSessionEntity create(String authSessionId, UUID userId,
        String authorizationId, String registeredClientId, String clientId,
        String oidcSid, String backChannelLogoutUri, Instant now) {
        var value = new OidcClientSessionEntity();
        value.id = UUID.randomUUID(); value.authSessionId = authSessionId;
        value.userId = userId; value.authorizationId = authorizationId;
        value.registeredClientId = registeredClientId; value.clientId = clientId;
        value.oidcSid = oidcSid; value.backChannelLogoutUri = backChannelLogoutUri;
        value.createdAt = now; value.lastAccessedAt = now;
        return value;
    }

    void refresh(String authorizationId, Instant now) {
        if (authorizationId != null) this.authorizationId = authorizationId;
        this.lastAccessedAt = now;
    }

    public UUID getId() { return id; }
    public String getAuthSessionId() { return authSessionId; }
    public UUID getUserId() { return userId; }
    public String getAuthorizationId() { return authorizationId; }
    public String getRegisteredClientId() { return registeredClientId; }
    public String getClientId() { return clientId; }
    public String getOidcSid() { return oidcSid; }
    public String getBackChannelLogoutUri() { return backChannelLogoutUri; }
}
