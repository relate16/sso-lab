package com.ssolab.auth.passwordless.totp;

import com.ssolab.auth.passwordless.crypto.EncryptedTotpSecret;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity(name = "TotpCredential")
@Table(name = "totp_credentials", schema = "auth")
public class TotpCredentialEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "secret_ciphertext", nullable = false, columnDefinition = "bytea")
    private byte[] secretCiphertext;

    @Column(name = "secret_iv", nullable = false, columnDefinition = "bytea")
    private byte[] secretIv;

    @Column(name = "secret_key_version", nullable = false)
    private int secretKeyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TotpCredentialStatus status;

    @Column(name = "last_verified_counter")
    private Long lastVerifiedCounter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TotpCredentialEntity() {
    }

    public static TotpCredentialEntity pending(
        UUID userId,
        EncryptedTotpSecret secret,
        Instant now
    ) {
        TotpCredentialEntity credential = new TotpCredentialEntity();
        credential.userId = Objects.requireNonNull(userId);
        credential.replacePendingSecret(secret, now);
        credential.createdAt = now;
        return credential;
    }

    public void replacePendingSecret(EncryptedTotpSecret secret, Instant now) {
        secretCiphertext = secret.ciphertext();
        secretIv = secret.iv();
        secretKeyVersion = secret.keyVersion();
        status = TotpCredentialStatus.PENDING;
        lastVerifiedCounter = null;
        activatedAt = null;
        updatedAt = Objects.requireNonNull(now);
    }

    public void activate(long verifiedCounter, Instant now) {
        status = TotpCredentialStatus.ACTIVE;
        lastVerifiedCounter = verifiedCounter;
        activatedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public boolean consumeCounter(long counter, Instant now) {
        if (lastVerifiedCounter != null && counter <= lastVerifiedCounter) {
            return false;
        }
        lastVerifiedCounter = counter;
        updatedAt = Objects.requireNonNull(now);
        return true;
    }

    public EncryptedTotpSecret encryptedSecret() {
        return new EncryptedTotpSecret(secretCiphertext, secretIv, secretKeyVersion);
    }

    public UUID getUserId() {
        return userId;
    }

    public TotpCredentialStatus getStatus() {
        return status;
    }

    public Long getLastVerifiedCounter() {
        return lastVerifiedCounter;
    }
}
