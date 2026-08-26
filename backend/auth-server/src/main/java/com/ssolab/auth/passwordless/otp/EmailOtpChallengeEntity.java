package com.ssolab.auth.passwordless.otp;

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

@Entity(name = "EmailOtpChallenge")
@Table(name = "email_otp_challenges", schema = "auth")
public class EmailOtpChallengeEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private OtpPurpose purpose;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserIdentityEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_registration_id")
    private PendingRegistrationEntity pendingRegistration;

    @Column(name = "verifier_hash", nullable = false, columnDefinition = "bytea")
    private byte[] verifierHash;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resend_available_at", nullable = false)
    private Instant resendAvailableAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmailOtpChallengeEntity() {
    }

    public static EmailOtpChallengeEntity signup(
        UUID id,
        PendingRegistrationEntity pending,
        byte[] verifierHash,
        int maxAttempts,
        Instant now,
        Instant expiresAt,
        Instant resendAvailableAt
    ) {
        return create(
            id, OtpPurpose.SIGNUP, null, Objects.requireNonNull(pending), verifierHash,
            maxAttempts, now, expiresAt, resendAvailableAt
        );
    }

    public static EmailOtpChallengeEntity login(
        UUID id,
        UserIdentityEntity user,
        byte[] verifierHash,
        int maxAttempts,
        Instant now,
        Instant expiresAt,
        Instant resendAvailableAt
    ) {
        return create(
            id, OtpPurpose.LOGIN, user, null, verifierHash,
            maxAttempts, now, expiresAt, resendAvailableAt
        );
    }

    public static EmailOtpChallengeEntity adminReauth(
        UUID id,
        UserIdentityEntity user,
        byte[] verifierHash,
        int maxAttempts,
        Instant now,
        Instant expiresAt,
        Instant resendAvailableAt
    ) {
        return create(
            id, OtpPurpose.ADMIN_REAUTH, Objects.requireNonNull(user), null, verifierHash,
            maxAttempts, now, expiresAt, resendAvailableAt
        );
    }

    private static EmailOtpChallengeEntity create(
        UUID id,
        OtpPurpose purpose,
        UserIdentityEntity user,
        PendingRegistrationEntity pending,
        byte[] verifierHash,
        int maxAttempts,
        Instant now,
        Instant expiresAt,
        Instant resendAvailableAt
    ) {
        EmailOtpChallengeEntity challenge = new EmailOtpChallengeEntity();
        challenge.id = Objects.requireNonNull(id);
        challenge.purpose = Objects.requireNonNull(purpose);
        challenge.user = user;
        challenge.pendingRegistration = pending;
        challenge.verifierHash = verifierHash.clone();
        challenge.failedAttempts = 0;
        challenge.maxAttempts = maxAttempts;
        challenge.createdAt = Objects.requireNonNull(now);
        challenge.updatedAt = now;
        challenge.expiresAt = Objects.requireNonNull(expiresAt);
        challenge.resendAvailableAt = Objects.requireNonNull(resendAvailableAt);
        return challenge;
    }

    public void recordFailure(Instant now) {
        if (failedAttempts < maxAttempts) {
            failedAttempts++;
        }
        updatedAt = Objects.requireNonNull(now);
    }

    public void consume(Instant now) {
        consumedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public void replaceVerifier(
        byte[] newVerifierHash,
        Instant expiresAt,
        Instant resendAvailableAt,
        Instant now
    ) {
        verifierHash = newVerifierHash.clone();
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.resendAvailableAt = Objects.requireNonNull(resendAvailableAt);
        updatedAt = Objects.requireNonNull(now);
    }

    public UUID getId() {
        return id;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public UserIdentityEntity getUser() {
        return user;
    }

    public PendingRegistrationEntity getPendingRegistration() {
        return pendingRegistration;
    }

    public byte[] getVerifierHash() {
        return verifierHash.clone();
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getResendAvailableAt() {
        return resendAvailableAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }
}
