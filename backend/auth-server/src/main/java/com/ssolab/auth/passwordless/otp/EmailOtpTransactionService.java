package com.ssolab.auth.passwordless.otp;

import com.ssolab.auth.admin.bootstrap.BootstrapAdminService;
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
    private final Clock clock;

    public EmailOtpTransactionService(
        PendingRegistrationRepository pendingRepository,
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
        Clock clock
    ) {
        this.pendingRepository = pendingRepository;
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
                challengeRepository.saveAndFlush(challenge);
                OtpMailPurpose mailPurpose = switch (expectedPurpose) {
                    case SIGNUP -> OtpMailPurpose.SIGNUP;
                    case LOGIN -> OtpMailPurpose.LOGIN;
                    case ADMIN_REAUTH -> OtpMailPurpose.ADMIN_REAUTH;
                    case EMAIL_CHANGE -> throw new IllegalArgumentException(
                        "email change resend is not implemented"
                    );
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
        UserIdentityEntity user = challenge.getUser();
        if (user == null || user.getStatus() != AccountStatus.ACTIVE) {
            return null;
        }
        return userIdentityService.decryptEmail(user);
    }
}
