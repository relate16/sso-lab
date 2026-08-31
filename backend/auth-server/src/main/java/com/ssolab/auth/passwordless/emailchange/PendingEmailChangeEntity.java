package com.ssolab.auth.passwordless.emailchange;

import com.ssolab.auth.identity.crypto.EncryptedEmail;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity(name = "PendingEmailChange")
@Table(name = "pending_email_changes", schema = "auth")
public class PendingEmailChangeEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserIdentityEntity user;

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

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    protected PendingEmailChangeEntity() {
    }

    public static PendingEmailChangeEntity create(
        UUID id,
        UserIdentityEntity user,
        EncryptedEmail email,
        byte[] lookupHash,
        Instant now,
        Instant expiresAt
    ) {
        PendingEmailChangeEntity pending = new PendingEmailChangeEntity();
        pending.id = Objects.requireNonNull(id);
        pending.user = Objects.requireNonNull(user);
        pending.emailCiphertext = email.ciphertext();
        pending.emailIv = email.iv();
        pending.emailKeyVersion = email.keyVersion();
        pending.emailLookupHash = Objects.requireNonNull(lookupHash).clone();
        pending.createdAt = Objects.requireNonNull(now);
        pending.expiresAt = Objects.requireNonNull(expiresAt);
        return pending;
    }

    public void extendExpiry(Instant expiresAt) {
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public void consume(Instant now) {
        consumedAt = Objects.requireNonNull(now);
    }

    public void invalidate(Instant now) {
        invalidatedAt = Objects.requireNonNull(now);
    }

    public boolean isUsableAt(Instant now) {
        return consumedAt == null && invalidatedAt == null && now.isBefore(expiresAt);
    }

    public EncryptedEmail encryptedEmail() {
        return new EncryptedEmail(emailCiphertext, emailIv, emailKeyVersion);
    }

    public UUID getId() { return id; }
    public UserIdentityEntity getUser() { return user; }
    public byte[] getEmailLookupHash() { return emailLookupHash.clone(); }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getInvalidatedAt() { return invalidatedAt; }
}
