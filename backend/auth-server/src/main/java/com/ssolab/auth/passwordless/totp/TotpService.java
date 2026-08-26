package com.ssolab.auth.passwordless.totp;

import com.ssolab.auth.identity.model.AccountStatus;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.identity.service.IdentityInputNormalizer;
import com.ssolab.auth.passwordless.crypto.EncryptedTotpSecret;
import com.ssolab.auth.passwordless.crypto.TotpSecretCipher;
import com.ssolab.auth.passwordless.recovery.RecoveryCodeBatch;
import com.ssolab.auth.passwordless.recovery.RecoveryCodeService;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TotpService {

    private final TotpCredentialRepository repository;
    private final UserIdentityRepository userRepository;
    private final IdentityInputNormalizer inputNormalizer;
    private final TotpAlgorithm algorithm;
    private final TotpSecretCipher cipher;
    private final RecoveryCodeService recoveryCodeService;
    private final Clock clock;

    public TotpService(
        TotpCredentialRepository repository,
        UserIdentityRepository userRepository,
        IdentityInputNormalizer inputNormalizer,
        TotpAlgorithm algorithm,
        TotpSecretCipher cipher,
        RecoveryCodeService recoveryCodeService,
        Clock clock
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.inputNormalizer = inputNormalizer;
        this.algorithm = algorithm;
        this.cipher = cipher;
        this.recoveryCodeService = recoveryCodeService;
        this.clock = clock;
    }

    @Transactional
    public TotpEnrollmentStart startEnrollment(UUID userId) {
        UserIdentityEntity user = requireActiveUser(userId);
        TotpCredentialEntity existing = repository.findLockedByUserId(userId).orElse(null);
        if (existing != null && existing.getStatus() == TotpCredentialStatus.ACTIVE) {
            throw new IllegalStateException("TOTP is already enrolled");
        }
        byte[] secret = algorithm.generateSecret();
        try {
            EncryptedTotpSecret encrypted = cipher.encrypt(userId, secret);
            Instant now = clock.instant();
            TotpCredentialEntity credential;
            if (existing == null) {
                credential = TotpCredentialEntity.pending(user.getId(), encrypted, now);
            } else {
                existing.replacePendingSecret(encrypted, now);
                credential = existing;
            }
            repository.saveAndFlush(credential);
            return new TotpEnrollmentStart(algorithm.otpauthUri(user.getUserId(), secret));
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    @Transactional
    public TotpEnrollmentResult confirmEnrollment(UUID userId, char[] candidate) {
        UserIdentityEntity user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getStatus() != AccountStatus.ACTIVE) {
            return TotpEnrollmentResult.failed(TotpVerificationStatus.ACCOUNT_UNAVAILABLE);
        }
        TotpCredentialEntity credential = repository.findLockedByUserId(userId).orElse(null);
        if (credential == null || credential.getStatus() != TotpCredentialStatus.PENDING) {
            return TotpEnrollmentResult.failed(TotpVerificationStatus.PENDING_ENROLLMENT_REQUIRED);
        }
        byte[] secret = cipher.decrypt(userId, credential.encryptedSecret());
        try {
            OptionalLong counter = algorithm.matchingCounter(secret, clock.instant(), candidate);
            if (counter.isEmpty()) {
                return TotpEnrollmentResult.failed(TotpVerificationStatus.INVALID);
            }
            credential.activate(counter.getAsLong(), clock.instant());
            repository.saveAndFlush(credential);
            RecoveryCodeBatch codes = recoveryCodeService.regenerate(userId);
            return new TotpEnrollmentResult(TotpVerificationStatus.SUCCESS, codes);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    @Transactional
    public TotpVerificationResult verifyLogin(String normalizedUserId, char[] candidate) {
        try {
            normalizedUserId = inputNormalizer.normalizeUserId(normalizedUserId);
        } catch (IllegalArgumentException exception) {
            return TotpVerificationResult.failed(TotpVerificationStatus.ACCOUNT_UNAVAILABLE);
        }
        UserIdentityEntity user = userRepository.findByNormalizedUserId(normalizedUserId).orElse(null);
        if (user == null || user.getStatus() != AccountStatus.ACTIVE) {
            return TotpVerificationResult.failed(TotpVerificationStatus.ACCOUNT_UNAVAILABLE);
        }
        TotpCredentialEntity credential = repository.findLockedByUserId(user.getId()).orElse(null);
        if (credential == null || credential.getStatus() != TotpCredentialStatus.ACTIVE) {
            return TotpVerificationResult.failed(TotpVerificationStatus.NOT_ENROLLED);
        }
        byte[] secret = cipher.decrypt(user.getId(), credential.encryptedSecret());
        try {
            OptionalLong counter = algorithm.matchingCounter(secret, clock.instant(), candidate);
            if (counter.isEmpty()) {
                return TotpVerificationResult.failed(TotpVerificationStatus.INVALID);
            }
            if (!credential.consumeCounter(counter.getAsLong(), clock.instant())) {
                return TotpVerificationResult.failed(TotpVerificationStatus.REPLAYED);
            }
            repository.saveAndFlush(credential);
            return new TotpVerificationResult(TotpVerificationStatus.SUCCESS, user.getId());
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    @Transactional
    public TotpVerificationResult verifyAdminReauth(UUID actorId, char[] candidate) {
        UserIdentityEntity user = userRepository.findById(actorId).orElse(null);
        if (user == null || user.getStatus() != AccountStatus.ACTIVE
            || !user.hasRole(com.ssolab.auth.identity.model.RoleName.ADMIN)) {
            return TotpVerificationResult.failed(TotpVerificationStatus.ACCOUNT_UNAVAILABLE);
        }
        TotpCredentialEntity credential = repository.findLockedByUserId(actorId).orElse(null);
        if (credential == null || credential.getStatus() != TotpCredentialStatus.ACTIVE) {
            return TotpVerificationResult.failed(TotpVerificationStatus.NOT_ENROLLED);
        }
        byte[] secret = cipher.decrypt(actorId, credential.encryptedSecret());
        try {
            OptionalLong counter = algorithm.matchingCounter(secret, clock.instant(), candidate);
            if (counter.isEmpty()) {
                return TotpVerificationResult.failed(TotpVerificationStatus.INVALID);
            }
            if (!credential.consumeCounter(counter.getAsLong(), clock.instant())) {
                return TotpVerificationResult.failed(TotpVerificationStatus.REPLAYED);
            }
            repository.saveAndFlush(credential);
            return new TotpVerificationResult(TotpVerificationStatus.SUCCESS, actorId);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    @Transactional
    public void disableForRecovery(UUID userId) {
        repository.findLockedByUserId(userId).ifPresent(repository::delete);
        repository.flush();
    }

    @Transactional
    public void disable(UUID userId) {
        requireActiveUser(userId);
        repository.findLockedByUserId(userId).ifPresent(repository::delete);
        repository.flush();
        recoveryCodeService.invalidateAll(userId);
    }

    @Transactional(readOnly = true)
    public boolean isEnrolled(UUID userId) {
        return repository.existsByUserIdAndStatus(userId, TotpCredentialStatus.ACTIVE);
    }

    private UserIdentityEntity requireActiveUser(UUID userId) {
        return userRepository.findById(userId)
            .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("identity is unavailable"));
    }
}
