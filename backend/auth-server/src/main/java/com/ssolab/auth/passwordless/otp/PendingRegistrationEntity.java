package com.ssolab.auth.passwordless.otp;

import com.ssolab.auth.identity.crypto.EncryptedEmail;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity(name = "PendingRegistration")
@Table(name = "pending_registrations", schema = "auth")
public class PendingRegistrationEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 30)
    private String userId;

    @Column(name = "normalized_user_id", nullable = false, length = 30)
    private String normalizedUserId;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "email_ciphertext", nullable = false, columnDefinition = "bytea")
    private byte[] emailCiphertext;

    @Column(name = "email_iv", nullable = false, columnDefinition = "bytea")
    private byte[] emailIv;

    @Column(name = "email_key_version", nullable = false)
    private int emailKeyVersion;

    @Column(name = "email_lookup_hash", nullable = false, columnDefinition = "bytea")
    private byte[] emailLookupHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected PendingRegistrationEntity() {
    }

    public static PendingRegistrationEntity create(
        UUID id,
        String normalizedUserId,
        String username,
        EncryptedEmail encryptedEmail,
        byte[] emailLookupHash,
        Instant now,
        Instant expiresAt
    ) {
        PendingRegistrationEntity pending = new PendingRegistrationEntity();
        pending.id = Objects.requireNonNull(id);
        pending.userId = Objects.requireNonNull(normalizedUserId);
        pending.normalizedUserId = normalizedUserId;
        pending.username = Objects.requireNonNull(username);
        pending.emailCiphertext = encryptedEmail.ciphertext();
        pending.emailIv = encryptedEmail.iv();
        pending.emailKeyVersion = encryptedEmail.keyVersion();
        pending.emailLookupHash = emailLookupHash.clone();
        pending.createdAt = Objects.requireNonNull(now);
        pending.expiresAt = Objects.requireNonNull(expiresAt);
        return pending;
    }

    public void extendExpiry(Instant expiresAt) {
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public void consume(Instant now) {
        this.consumedAt = Objects.requireNonNull(now);
    }

    public EncryptedEmail encryptedEmail() {
        return new EncryptedEmail(emailCiphertext, emailIv, emailKeyVersion);
    }

    public UUID getId() {
        return id;
    }

    public String getNormalizedUserId() {
        return normalizedUserId;
    }

    public String getUsername() {
        return username;
    }

    public byte[] getEmailLookupHash() {
        return emailLookupHash.clone();
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }
}
