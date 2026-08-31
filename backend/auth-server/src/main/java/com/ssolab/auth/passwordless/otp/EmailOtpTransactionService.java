package com.ssolab.auth.passwordless.otp;

import com.ssolab.auth.admin.bootstrap.BootstrapAdminService;
import com.ssolab.auth.audit.AuditEvent;
import com.ssolab.auth.audit.AuditService;
import com.ssolab.auth.audit.AuditSource;
import com.ssolab.auth.identity.crypto.EmailCipher;
import com.ssolab.auth.identity.crypto.EmailLookupHasher;
import com.ssolab.auth.identity.crypto.EmailNormalizer;
import com.ssolab.auth.identity.crypto.EncryptedEmail;
import com.ssolab.auth.identity.model.AccountStatus;
import com.ssolab.auth.identity.model.RoleName;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.identity.service.CreateUserIdentityCommand;
import com.ssolab.auth.identity.service.IdentityConflictException;
import com.ssolab.auth.identity.service.IdentityInputNormalizer;
import com.ssolab.auth.identity.service.UserIdentityService;
import com.ssolab.auth.passwordless.crypto.OtpCodeGenerator;
import com.ssolab.auth.passwordless.crypto.OtpVerifier;
import com.ssolab.auth.passwordless.crypto.SensitiveCode;
import com.ssolab.auth.passwordless.emailchange.EmailChangeVerificationResult;
import com.ssolab.auth.passwordless.emailchange.PendingEmailChangeEntity;
import com.ssolab.auth.passwordless.emailchange.PendingEmailChangeRepository;
import com.ssolab.auth.passwordless.mail.OtpMailPurpose;
import com.ssolab.auth.systemconfig.IdentityPolicyConfig;
import com.ssolab.auth.systemconfig.TypedSystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailOtpTransactionService {

    private final PendingRegistrationRepository pendingRepository;
    private final PendingEmailChangeRepository pendingEmailChanges;
    private final EmailOtpChallengeRepository challengeRepository;
    private final UserIdentityRepository userRepository;
    private final UserIdentityService userIdentityService;
    private final IdentityInputNormalizer inputNormalizer;
    private final EmailNormalizer emailNormalizer;
    private final EmailCipher emailCipher;
    private final EmailLookupHasher emailLookupHasher;
    private final OtpCodeGenerator codeGenerator;
    private final OtpVerifier otpVerifier;
    private final TypedSystemConfigService configService;
    private final BootstrapAdminService bootstrapAdminService;
    private final AuditService auditService;
    private final Clock clock;

    public EmailOtpTransactionService(
        PendingRegistrationRepository pendingRepository,
        PendingEmailChangeRepository pendingEmailChanges,
        EmailOtpChallengeRepository challengeRepository,
        UserIdentityRepository userRepository,
        UserIdentityService userIdentityService,
        IdentityInputNormalizer inputNormalizer,
        EmailNormalizer emailNormalizer,
        EmailCipher emailCipher,
        EmailLookupHasher emailLookupHasher,
        OtpCodeGenerator codeGenerator,
        OtpVerifier otpVerifier,
        TypedSystemConfigService configService,
        BootstrapAdminService bootstrapAdminService,
        AuditService auditService,
        Clock clock
    ) {
        this.pendingRepository = pendingRepository;
        this.pendingEmailChanges = pendingEmailChanges;
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.userIdentityService = userIdentityService;
        this.inputNormalizer = inputNormalizer;
        this.emailNormalizer = emailNormalizer;
        this.emailCipher = emailCipher;
        this.emailLookupHasher = emailLookupHasher;
        this.codeGenerator = codeGenerator;
        this.otpVerifier = otpVerifier;
        this.configService = configService;
        this.bootstrapAdminService = bootstrapAdminService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public OtpDelivery issueSignup(CreateUserIdentityCommand command) {
        String normalizedUserId = inputNormalizer.normalizeUserId(command.userId());
        String username = inputNormalizer.normalizeUsername(command.username());
        String normalizedEmail = emailNormalizer.normalize(command.email());
        byte[] emailHash = emailLookupHasher.hash(normalizedEmail);
        IdentityPolicyConfig policy = configService.identityPolicy();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(policy.emailOtpTtl());
        UUID challengeId = UUID.randomUUID();
        try (SensitiveCode code = codeGenerator.generate()) {
            EncryptedEmail encryptedEmail = emailCipher.encrypt(normalizedEmail);
            PendingRegistrationEntity pending = PendingRegistrationEntity.create(
                UUID.randomUUID(), normalizedUserId, username, encryptedEmail, emailHash,
                now, expiresAt
            );
            pendingRepository.save(pending);
            char[] codeValue = code.copy();
            try {
                EmailOtpChallengeEntity challenge = EmailOtpChallengeEntity.signup(
                    challengeId,
                    pending,
                    otpVerifier.hash(challengeId, OtpPurpose.SIGNUP.name(), codeValue),
                    policy.emailOtpMaxAttempts(),
                    now,
                    expiresAt,
                    now.plus(policy.emailOtpResendInterval())
                );
                challengeRepository.saveAndFlush(challenge);
                return new OtpDelivery(
                    challengeId,
                    OtpMailPurpose.SIGNUP,
                    normalizedEmail,
                    SensitiveCode.of(codeValue),
                    expiresAt,
                    challenge.getResendAvailableAt()
                );
            } finally {
                java.util.Arrays.fill(codeValue, '\0');
            }
        }
    }

    @Transactional
    public OtpDelivery issueLogin(String userId) {
        UserIdentityEntity user = null;
        try {
            String normalizedUserId = inputNormalizer.normalizeUserId(userId);
            user = userRepository.findByNormalizedUserId(normalizedUserId)
                .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
                .orElse(null);
        } catch (IllegalArgumentException ignored) {
            // Invalid and unknown identifiers follow the same decoy challenge path.
        }
        IdentityPolicyConfig policy = configService.identityPolicy();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(policy.emailOtpTtl());
        UUID challengeId = UUID.randomUUID();
        try (SensitiveCode code = codeGenerator.generate()) {
            char[] codeValue = code.copy();
            try {
                EmailOtpChallengeEntity challenge = EmailOtpChallengeEntity.login(
                    challengeId,
                    user,
                    otpVerifier.hash(challengeId, OtpPurpose.LOGIN.name(), codeValue),
                    policy.emailOtpMaxAttempts(),
                    now,
                    expiresAt,
                    now.plus(policy.emailOtpResendInterval())
                );
                challengeRepository.saveAndFlush(challenge);
                String recipient = user == null ? null : userIdentityService.decryptEmail(user);
                return new OtpDelivery(
                    challengeId,
                    OtpMailPurpose.LOGIN,
                    recipient,
                    SensitiveCode.of(codeValue),
                    expiresAt,
                    challenge.getResendAvailableAt()
                );
            } finally {
                java.util.Arrays.fill(codeValue, '\0');
            }
        }
    }

    @Transactional
    public OtpDelivery issueEmailChange(UUID userId, String newEmail) {
        UserIdentityEntity user = userRepository.findLockedById(userId)
            .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("identity is unavailable"));
        String normalizedEmail = emailNormalizer.normalize(newEmail);
        byte[] emailHash = emailLookupHasher.hash(normalizedEmail);
        if (java.security.MessageDigest.isEqual(user.getEmailLookupHash(), emailHash)
            || userRepository.existsByEmailLookupHash(emailHash)) {
            throw new IdentityConflictException("email is unavailable");
        }

        Instant now = clock.instant();
        pendingEmailChanges.findActiveLockedByUserId(userId).forEach(pending ->
            pending.invalidate(now)
        );
        challengeRepository.findByUser_IdAndPurposeAndConsumedAtIsNull(
            userId, OtpPurpose.EMAIL_CHANGE
        ).forEach(challenge -> challenge.consume(now));
        pendingEmailChanges.flush();
        challengeRepository.flush();

        IdentityPolicyConfig policy = configService.identityPolicy();
        Instant expiresAt = now.plus(policy.emailOtpTtl());
        PendingEmailChangeEntity pending = PendingEmailChangeEntity.create(
            UUID.randomUUID(),
            user,
            emailCipher.encrypt(normalizedEmail),
            emailHash,
            now,
            expiresAt
        );
        pendingEmailChanges.saveAndFlush(pending);

        UUID challengeId = UUID.randomUUID();
        try (SensitiveCode code = codeGenerator.generate()) {
            char[] codeValue = code.copy();
            try {
                EmailOtpChallengeEntity challenge = EmailOtpChallengeEntity.emailChange(
                    challengeId,
                    user,
                    pending,
                    otpVerifier.hash(challengeId, OtpPurpose.EMAIL_CHANGE.name(), codeValue),
                    policy.emailOtpMaxAttempts(),
                    now,
                    expiresAt,
                    now.plus(policy.emailOtpResendInterval())
                );
                challengeRepository.saveAndFlush(challenge);
                return new OtpDelivery(
                    challengeId,
                    OtpMailPurpose.EMAIL_CHANGE,
                    normalizedEmail,
                    SensitiveCode.of(codeValue),
                    expiresAt,
                    challenge.getResendAvailableAt()
                );
            } finally {
                java.util.Arrays.fill(codeValue, '\0');
            }
        }
    }

    @Transactional
    public OtpDelivery issueAdminReauth(UUID actorId) {
        UserIdentityEntity actor = userRepository.findById(actorId)
            .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
            .filter(candidate -> candidate.hasRole(RoleName.ADMIN))
            .orElseThrow(() -> new IllegalArgumentException("admin identity is unavailable"));
        EmailOtpChallengeEntity existing = challengeRepository
            .findFirstByUser_IdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                actorId, OtpPurpose.ADMIN_REAUTH
            ).orElse(null);
        Instant now = clock.instant();
        if (existing != null && now.isBefore(existing.getExpiresAt())) {
            return resend(existing.getId(), OtpPurpose.ADMIN_REAUTH);
        }

        IdentityPolicyConfig policy = configService.identityPolicy();
        Instant expiresAt = now.plus(policy.emailOtpTtl());
        UUID challengeId = UUID.randomUUID();
        try (SensitiveCode code = codeGenerator.generate()) {
            char[] codeValue = code.copy();
            try {
                EmailOtpChallengeEntity challenge = EmailOtpChallengeEntity.adminReauth(
                    challengeId,
                    actor,
                    otpVerifier.hash(challengeId, OtpPurpose.ADMIN_REAUTH.name(), codeValue),
                    policy.emailOtpMaxAttempts(),
                    now,
                    expiresAt,
                    now.plus(policy.emailOtpResendInterval())
                );
                challengeRepository.saveAndFlush(challenge);
                return new OtpDelivery(
                    challengeId,
                    OtpMailPurpose.ADMIN_REAUTH,
                    userIdentityService.decryptEmail(actor),
                    SensitiveCode.of(codeValue),
                    expiresAt,
                    challenge.getResendAvailableAt()
                );
            } finally {
                java.util.Arrays.fill(codeValue, '\0');
            }
        }
    }

    @Transactional
    public OtpDelivery resend(UUID challengeId, OtpPurpose expectedPurpose) {
        EmailOtpChallengeEntity challenge = challengeRepository.findLockedById(challengeId)
            .orElse(null);
        Instant now = clock.instant();
        if (challenge == null || challenge.getPurpose() != expectedPurpose) {
            return null;
        }
        if (challenge.getConsumedAt() != null
            || !now.isBefore(challenge.getExpiresAt())
            || challenge.getFailedAttempts() >= challenge.getMaxAttempts()
            || now.isBefore(challenge.getResendAvailableAt())) {
            return null;
        }

        IdentityPolicyConfig policy = configService.identityPolicy();
        Instant expiresAt = now.plus(policy.emailOtpTtl());
        try (SensitiveCode code = codeGenerator.generate()) {
            char[] codeValue = code.copy();
            try {
                challenge.replaceVerifier(
                    otpVerifier.hash(challengeId, expectedPurpose.name(), codeValue),
                    expiresAt,
                    now.plus(policy.emailOtpResendInterval()),
                    now
                );
                String recipient = recipient(challenge);
                if (challenge.getPendingRegistration() != null) {
                    challenge.getPendingRegistration().extendExpiry(expiresAt);
                }
                if (challenge.getPendingEmailChange() != null) {
                    challenge.getPendingEmailChange().extendExpiry(expiresAt);
                }
                challengeRepository.saveAndFlush(challenge);
                OtpMailPurpose mailPurpose = switch (expectedPurpose) {
                    case SIGNUP -> OtpMailPurpose.SIGNUP;
                    case LOGIN -> OtpMailPurpose.LOGIN;
                    case ADMIN_REAUTH -> OtpMailPurpose.ADMIN_REAUTH;
                    case EMAIL_CHANGE -> OtpMailPurpose.EMAIL_CHANGE;
                };
                return new OtpDelivery(
                    challengeId,
                    mailPurpose,
                    recipient,
                    SensitiveCode.of(codeValue),
                    expiresAt,
                    challenge.getResendAvailableAt()
                );
            } finally {
                java.util.Arrays.fill(codeValue, '\0');
            }
        }
    }

    @Transactional
    public OtpDelivery resendEmailChange(UUID userId, UUID challengeId) {
        EmailOtpChallengeEntity challenge = challengeRepository.findLockedById(challengeId)
            .orElse(null);
        if (challenge == null
            || challenge.getPurpose() != OtpPurpose.EMAIL_CHANGE
            || challenge.getUser() == null
            || !challenge.getUser().getId().equals(userId)) {
            return null;
        }
        return resend(challengeId, OtpPurpose.EMAIL_CHANGE);
    }

    @Transactional
    public SignupVerificationResult verifySignup(UUID challengeId, char[] candidate) {
        EmailOtpChallengeEntity challenge = challengeRepository.findLockedById(challengeId)
            .orElse(null);
        OtpVerificationStatus status = verify(challenge, OtpPurpose.SIGNUP, candidate);
        if (status != OtpVerificationStatus.SUCCESS) {
            return SignupVerificationResult.failed(status);
        }
        PendingRegistrationEntity pending = challenge.getPendingRegistration();
        try {
            UserIdentityEntity user = userIdentityService.createVerifiedIdentity(
                pending.getNormalizedUserId(),
                pending.getUsername(),
                pending.encryptedEmail(),
                pending.getEmailLookupHash()
            );
            bootstrapAdminService.claimIfEligible(user);
            Instant now = clock.instant();
            challenge.consume(now);
            pending.consume(now);
            challengeRepository.saveAndFlush(challenge);
            return new SignupVerificationResult(OtpVerificationStatus.SUCCESS, user.getId());
        } catch (IdentityConflictException exception) {
            Instant now = clock.instant();
            challenge.consume(now);
            pending.consume(now);
            challengeRepository.saveAndFlush(challenge);
            return SignupVerificationResult.failed(OtpVerificationStatus.IDENTITY_CONFLICT);
        }
    }

    @Transactional
    public LoginVerificationResult verifyLogin(UUID challengeId, char[] candidate) {
        EmailOtpChallengeEntity challenge = challengeRepository.findLockedById(challengeId)
            .orElse(null);
        OtpVerificationStatus status = verify(challenge, OtpPurpose.LOGIN, candidate);
        if (status != OtpVerificationStatus.SUCCESS) {
            return LoginVerificationResult.failed(status);
        }
        UserIdentityEntity user = challenge.getUser();
        if (user == null || user.getStatus() != AccountStatus.ACTIVE) {
            challenge.recordFailure(clock.instant());
            challengeRepository.saveAndFlush(challenge);
            return LoginVerificationResult.failed(OtpVerificationStatus.ACCOUNT_UNAVAILABLE);
        }
        challenge.consume(clock.instant());
        challengeRepository.saveAndFlush(challenge);
        return new LoginVerificationResult(OtpVerificationStatus.SUCCESS, user.getId());
    }

    @Transactional
    public LoginVerificationResult verifyAdminReauth(
        UUID actorId,
        UUID challengeId,
        char[] candidate
    ) {
        EmailOtpChallengeEntity challenge = challengeRepository.findLockedById(challengeId)
            .orElse(null);
        OtpVerificationStatus status = verify(challenge, OtpPurpose.ADMIN_REAUTH, candidate);
        if (status != OtpVerificationStatus.SUCCESS) {
            return LoginVerificationResult.failed(status);
        }
        UserIdentityEntity user = challenge.getUser();
        if (user == null || !user.getId().equals(actorId)
            || user.getStatus() != AccountStatus.ACTIVE || !user.hasRole(RoleName.ADMIN)) {
            challenge.recordFailure(clock.instant());
            challengeRepository.saveAndFlush(challenge);
            return LoginVerificationResult.failed(OtpVerificationStatus.ACCOUNT_UNAVAILABLE);
        }
        challenge.consume(clock.instant());
        challengeRepository.saveAndFlush(challenge);
        return new LoginVerificationResult(OtpVerificationStatus.SUCCESS, user.getId());
    }

    @Transactional
    public EmailChangeVerificationResult verifyEmailChange(
        UUID userId,
        UUID challengeId,
        char[] candidate,
        String traceId
    ) {
        EmailOtpChallengeEntity challenge = challengeRepository.findLockedById(challengeId)
            .orElse(null);
        OtpVerificationStatus status = verify(challenge, OtpPurpose.EMAIL_CHANGE, candidate);
        if (status != OtpVerificationStatus.SUCCESS) {
            return EmailChangeVerificationResult.failed(status);
        }
        PendingEmailChangeEntity pending = challenge.getPendingEmailChange();
        Instant now = clock.instant();
        if (challenge.getUser() == null
            || !challenge.getUser().getId().equals(userId)
            || pending == null
            || !pending.getUser().getId().equals(userId)
            || !pending.isUsableAt(now)) {
            challenge.recordFailure(now);
            challengeRepository.saveAndFlush(challenge);
            return EmailChangeVerificationResult.failed(OtpVerificationStatus.ACCOUNT_UNAVAILABLE);
        }

        UserIdentityEntity user = userRepository.findLockedById(userId)
            .filter(candidateUser -> candidateUser.getStatus() == AccountStatus.ACTIVE)
            .orElse(null);
        if (user == null) {
            return EmailChangeVerificationResult.failed(OtpVerificationStatus.ACCOUNT_UNAVAILABLE);
        }
        byte[] lookupHash = pending.getEmailLookupHash();
        if (userRepository.existsByEmailLookupHash(lookupHash)) {
            throw new IdentityConflictException("email is unavailable");
        }
        user.changeEmail(pending.encryptedEmail(), lookupHash, now);
        challenge.consume(now);
        pending.consume(now);
        try {
            userRepository.saveAndFlush(user);
            challengeRepository.saveAndFlush(challenge);
            pendingEmailChanges.saveAndFlush(pending);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw new IdentityConflictException("email is unavailable", exception);
        }
        auditService.recordWithinTransaction(
            AuditEvent.EMAIL_CHANGED,
            userId,
            userId,
            true,
            AuditSource.AUTH_WEB,
            traceId
        );
        return new EmailChangeVerificationResult(OtpVerificationStatus.SUCCESS, userId);
    }

    private OtpVerificationStatus verify(
        EmailOtpChallengeEntity challenge,
        OtpPurpose expectedPurpose,
        char[] candidate
    ) {
        if (challenge == null || challenge.getPurpose() != expectedPurpose) {
            return OtpVerificationStatus.NOT_FOUND;
        }
        Instant now = clock.instant();
        if (challenge.getConsumedAt() != null) {
            return OtpVerificationStatus.ALREADY_USED;
        }
        if (!now.isBefore(challenge.getExpiresAt())) {
            return OtpVerificationStatus.EXPIRED;
        }
        if (challenge.getFailedAttempts() >= challenge.getMaxAttempts()) {
            return OtpVerificationStatus.ATTEMPTS_EXCEEDED;
        }
        if (!otpVerifier.matches(
            challenge.getVerifierHash(), challenge.getId(), expectedPurpose.name(), candidate
        )) {
            challenge.recordFailure(now);
            challengeRepository.saveAndFlush(challenge);
            return challenge.getFailedAttempts() >= challenge.getMaxAttempts()
                ? OtpVerificationStatus.ATTEMPTS_EXCEEDED
                : OtpVerificationStatus.INVALID;
        }
        return OtpVerificationStatus.SUCCESS;
    }

    private String recipient(EmailOtpChallengeEntity challenge) {
        if (challenge.getPendingRegistration() != null) {
            return emailCipher.decrypt(challenge.getPendingRegistration().encryptedEmail());
        }
        if (challenge.getPendingEmailChange() != null) {
            return challenge.getPendingEmailChange().isUsableAt(clock.instant())
                ? emailCipher.decrypt(challenge.getPendingEmailChange().encryptedEmail())
                : null;
        }
        UserIdentityEntity user = challenge.getUser();
        if (user == null || user.getStatus() != AccountStatus.ACTIVE) {
            return null;
        }
        return userIdentityService.decryptEmail(user);
    }
}
