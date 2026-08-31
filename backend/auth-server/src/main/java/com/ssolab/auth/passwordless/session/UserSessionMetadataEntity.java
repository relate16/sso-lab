package com.ssolab.auth.passwordless.session;

import com.ssolab.auth.identity.model.UserIdentityEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity(name = "UserSessionMetadata")
@Table(name = "user_session_metadata", schema = "auth")
public class UserSessionMetadataEntity {

    @Id
    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "device_label", nullable = false, length = 160)
    private String deviceLabel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserIdentityEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "authentication_method", nullable = false, length = 30)
    private AuthenticationMethod authenticationMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_scope", nullable = false, length = 30)
    private SessionScope scope;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_accessed_at", nullable = false)
    private Instant lastAccessedAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "reauthenticated_at", nullable = false)
    private Instant reauthenticatedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    protected UserSessionMetadataEntity() {
    }

    public static UserSessionMetadataEntity create(
        String sessionId,
        UserIdentityEntity user,
        AuthenticationMethod method,
        SessionScope scope,
        String deviceLabel,
        Instant now,
        Instant absoluteExpiresAt
    ) {
        UserSessionMetadataEntity metadata = new UserSessionMetadataEntity();
        metadata.sessionId = Objects.requireNonNull(sessionId);
        metadata.publicId = UUID.randomUUID();
        metadata.deviceLabel = Objects.requireNonNull(deviceLabel);
        metadata.user = Objects.requireNonNull(user);
        metadata.authenticationMethod = Objects.requireNonNull(method);
        metadata.scope = Objects.requireNonNull(scope);
        metadata.createdAt = Objects.requireNonNull(now);
        metadata.lastAccessedAt = now;
        metadata.absoluteExpiresAt = Objects.requireNonNull(absoluteExpiresAt);
        metadata.reauthenticatedAt = now;
        return metadata;
    }

    public void touch(Instant now) {
        lastAccessedAt = Objects.requireNonNull(now);
    }

    public void invalidate(Instant now) {
        invalidatedAt = Objects.requireNonNull(now);
    }

    public void markReauthenticated(Instant now) {
        reauthenticatedAt = Objects.requireNonNull(now);
        lastAccessedAt = now;
    }

    public String getSessionId() {
        return sessionId;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public UserIdentityEntity getUser() {
        return user;
    }

    public AuthenticationMethod getAuthenticationMethod() {
        return authenticationMethod;
    }

    public SessionScope getScope() {
        return scope;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public Instant getAbsoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    public Instant getReauthenticatedAt() {
        return reauthenticatedAt;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }
}
