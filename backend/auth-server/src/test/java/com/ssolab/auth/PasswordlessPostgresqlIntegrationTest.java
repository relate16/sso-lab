package com.ssolab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.identity.service.CreateUserIdentityCommand;
import com.ssolab.auth.identity.service.UserIdentityService;
import com.ssolab.auth.passwordless.mail.CapturedOtpMail;
import com.ssolab.auth.passwordless.mail.InMemoryVerificationMailSender;
import com.ssolab.auth.passwordless.otp.LoginVerificationResult;
import com.ssolab.auth.passwordless.otp.OtpRequestResult;
import com.ssolab.auth.passwordless.otp.OtpVerificationStatus;
import com.ssolab.auth.passwordless.otp.PasswordlessEmailOtpService;
import com.ssolab.auth.passwordless.otp.SignupVerificationResult;
import com.ssolab.auth.passwordless.recovery.RecoveryCodeBatch;
import com.ssolab.auth.passwordless.recovery.RecoveryCodeService;
import com.ssolab.auth.passwordless.recovery.TotpRecoveryResult;
import com.ssolab.auth.passwordless.recovery.TotpRecoveryService;
import com.ssolab.auth.passwordless.session.AuthenticationMethod;
import com.ssolab.auth.passwordless.session.AuthSessionService;
import com.ssolab.auth.passwordless.session.SessionScope;
import com.ssolab.auth.passwordless.session.SessionValidationStatus;
import com.ssolab.auth.passwordless.totp.TotpAlgorithm;
import com.ssolab.auth.passwordless.totp.TotpEnrollmentResult;
import com.ssolab.auth.passwordless.totp.TotpEnrollmentStart;
import com.ssolab.auth.passwordless.totp.TotpService;
import com.ssolab.auth.passwordless.totp.TotpVerificationResult;
import com.ssolab.auth.passwordless.totp.TotpVerificationStatus;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PasswordlessPostgresqlIntegrationTest {

    private static final String TEST_EMAIL_ENCRYPTION_KEY = testKey((byte) 0x11);
    private static final String TEST_EMAIL_LOOKUP_HMAC_KEY = testKey((byte) 0x22);
    private static final String TEST_OTP_HMAC_KEY = testKey((byte) 0x33);
    private static final String TEST_TOTP_ENCRYPTION_KEY = testKey((byte) 0x44);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("SSO_EMAIL_ENCRYPTION_KEY", () -> TEST_EMAIL_ENCRYPTION_KEY);
        registry.add("SSO_EMAIL_LOOKUP_HMAC_KEY", () -> TEST_EMAIL_LOOKUP_HMAC_KEY);
        registry.add("SSO_OTP_HMAC_KEY", () -> TEST_OTP_HMAC_KEY);
        registry.add("SSO_TOTP_ENCRYPTION_KEY", () -> TEST_TOTP_ENCRYPTION_KEY);
        registry.add("SESSION_COOKIE_SECURE", () -> "false");
        OidcTestProperties.register(registry);
    }

    @Autowired PasswordlessEmailOtpService emailOtpService;
    @Autowired InMemoryVerificationMailSender mailSender;
    @Autowired UserIdentityService userIdentityService;
    @Autowired UserIdentityRepository userRepository;
    @Autowired TotpService totpService;
    @Autowired TotpRecoveryService totpRecoveryService;
    @Autowired RecoveryCodeService recoveryCodeService;
    @Autowired TotpAlgorithm totpAlgorithm;
    @Autowired AuthSessionService sessionService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;
    @Autowired Clock clock;
    @Autowired MockMvc mvc;

    @BeforeEach
    void clearMailSink() {
        mailSender.clear();
    }

    @Test
    void appliesPasswordlessMigrationWithoutPlaintextSecretColumns() {
        Integer migrations = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.flyway_schema_history WHERE version IN ('1', '2', '3')",
            Integer.class
        );
        Integer tables = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='auth' AND "
                + "table_name IN ('pending_registrations','email_otp_challenges',"
                + "'totp_credentials','recovery_codes','user_session_metadata')",
            Integer.class
        );
        Integer forbiddenColumns = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='auth' AND ("
                + "(table_name='email_otp_challenges' AND column_name IN ('otp','code')) OR "
                + "(table_name='totp_credentials' AND column_name IN ('secret','secret_plaintext')) OR "
                + "(table_name='recovery_codes' AND column_name IN ('code','plaintext_code')))",
            Integer.class
        );

        assertThat(migrations).isEqualTo(3);
        assertThat(tables).isEqualTo(5);
        assertThat(forbiddenColumns).isZero();
    }

    @Test
    void completesSignupOtpAndPreventsReuse() {
        String suffix = suffix();
        OtpRequestResult request = emailOtpService.startSignup(new CreateUserIdentityCommand(
            "signup." + suffix, "가입 사용자", "signup." + suffix + "@example.com"
        ));
        char[] code = capturedCode(request.challengeId());
        try {
            SignupVerificationResult verified = emailOtpService.verifySignup(
                request.challengeId(), code
            );
            SignupVerificationResult reused = emailOtpService.verifySignup(
                request.challengeId(), code
            );

            assertThat(verified.status()).isEqualTo(OtpVerificationStatus.SUCCESS);
            assertThat(reused.status()).isEqualTo(OtpVerificationStatus.ALREADY_USED);
            assertThat(userRepository.findById(verified.userId())).isPresent();
        } finally {
            Arrays.fill(code, '\0');
        }
    }

    @Test
    void enforcesOtpExpiryAttemptLimitAndResendInterval() {
        UserIdentityEntity expiryUser = createUser("expire");
        OtpRequestResult expiredRequest = emailOtpService.sendLoginOtp(expiryUser.getUserId());
        char[] expiredCode = capturedCode(expiredRequest.challengeId());
        jdbcTemplate.update(
            "UPDATE auth.email_otp_challenges SET created_at=now()-interval '10 minutes', "
                + "expires_at=now()-interval '1 second' WHERE id=?",
            expiredRequest.challengeId()
        );
        assertThat(emailOtpService.verifyLogin(expiredRequest.challengeId(), expiredCode).status())
            .isEqualTo(OtpVerificationStatus.EXPIRED);
        Arrays.fill(expiredCode, '\0');

        UserIdentityEntity attemptsUser = createUser("attempts");
        OtpRequestResult attemptsRequest = emailOtpService.sendLoginOtp(attemptsUser.getUserId());
        char[] actual = capturedCode(attemptsRequest.challengeId());
        char[] wrong = differentCode(actual);
        LoginVerificationResult finalAttempt = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            finalAttempt = emailOtpService.verifyLogin(attemptsRequest.challengeId(), wrong);
        }
        assertThat(finalAttempt.status()).isEqualTo(OtpVerificationStatus.ATTEMPTS_EXCEEDED);
        assertThat(emailOtpService.verifyLogin(attemptsRequest.challengeId(), actual).status())
            .isEqualTo(OtpVerificationStatus.ATTEMPTS_EXCEEDED);
        Arrays.fill(actual, '\0');
        Arrays.fill(wrong, '\0');

        UserIdentityEntity resendUser = createUser("resend");
        OtpRequestResult first = emailOtpService.sendLoginOtp(resendUser.getUserId());
        assertThat(emailOtpService.resendLogin(first.challengeId()).status())
            .isEqualTo(OtpVerificationStatus.RESEND_TOO_SOON);
        jdbcTemplate.update(
            "UPDATE auth.email_otp_challenges SET resend_available_at = now() - interval '1 second' WHERE id=?",
            first.challengeId()
        );
        OtpRequestResult resent = emailOtpService.resendLogin(first.challengeId());
        assertThat(resent.status()).isEqualTo(OtpVerificationStatus.SUCCESS);
    }

    @Test
    void verifiesLoginOtpOnceAndBlocksSuspendedUser() {
        UserIdentityEntity user = createUser("login");
        OtpRequestResult request = emailOtpService.sendLoginOtp(user.getUserId());
        char[] code = capturedCode(request.challengeId());
        try {
            assertThat(emailOtpService.verifyLogin(request.challengeId(), code).status())
                .isEqualTo(OtpVerificationStatus.SUCCESS);
            assertThat(emailOtpService.verifyLogin(request.challengeId(), code).status())
                .isEqualTo(OtpVerificationStatus.ALREADY_USED);
        } finally {
            Arrays.fill(code, '\0');
        }

        UserIdentityEntity suspended = createUser("suspended");
        OtpRequestResult suspendedRequest = emailOtpService.sendLoginOtp(suspended.getUserId());
        char[] suspendedCode = capturedCode(suspendedRequest.challengeId());
        jdbcTemplate.update(
            "UPDATE auth.users SET account_status='SUSPENDED' WHERE id=?",
            suspended.getId()
        );
        entityManager.clear();
        try {
            assertThat(emailOtpService.verifyLogin(suspendedRequest.challengeId(), suspendedCode).status())
                .isEqualTo(OtpVerificationStatus.ACCOUNT_UNAVAILABLE);
        } finally {
            Arrays.fill(suspendedCode, '\0');
        }
    }

    @Test
    void enrollsAndVerifiesTotpWithReplayProtectionAndEncryptedStorage() {
        UserIdentityEntity user = createUser("totp");
        TotpEnrollmentStart start = totpService.startEnrollment(user.getId());
        byte[] secret = secretFromUri(start.otpauthUri());
        char[] enrollmentCode = totpAlgorithm.generateCode(secret, clock.instant()).toCharArray();
        TotpEnrollmentResult enrollment = totpService.confirmEnrollment(user.getId(), enrollmentCode);
        Arrays.fill(enrollmentCode, '\0');

        char[] nextCode = totpAlgorithm.generateCode(
            secret, clock.instant().plusSeconds(TotpAlgorithm.PERIOD_SECONDS)
        ).toCharArray();
        TotpVerificationResult login = totpService.verifyLogin(user.getUserId(), nextCode);
        TotpVerificationResult replay = totpService.verifyLogin(user.getUserId(), nextCode);

        byte[] stored = jdbcTemplate.queryForObject(
            "SELECT secret_ciphertext FROM auth.totp_credentials WHERE user_id=?",
            byte[].class,
            user.getId()
        );
        assertThat(enrollment.status()).isEqualTo(TotpVerificationStatus.SUCCESS);
        assertThat(enrollment.recoveryCodes().codes()).hasSize(10).doesNotHaveDuplicates();
        assertThat(login.status()).isEqualTo(TotpVerificationStatus.SUCCESS);
        assertThat(replay.status()).isEqualTo(TotpVerificationStatus.REPLAYED);
        assertThat(stored).isNotEqualTo(secret);
        Arrays.fill(nextCode, '\0');
        Arrays.fill(secret, (byte) 0);
    }

    @Test
    void consumesRecoveryCodeOnceAndOnlyCreatesRestrictedEnrollmentFlow() {
        UserIdentityEntity user = createUser("recovery");
        TotpEnrollmentStart initial = totpService.startEnrollment(user.getId());
        byte[] initialSecret = secretFromUri(initial.otpauthUri());
        char[] initialCode = totpAlgorithm.generateCode(initialSecret, clock.instant()).toCharArray();
        RecoveryCodeBatch codes = totpService.confirmEnrollment(user.getId(), initialCode)
            .recoveryCodes();
        Arrays.fill(initialCode, '\0');
        Arrays.fill(initialSecret, (byte) 0);

        TotpRecoveryResult recovery = totpRecoveryService.start(user.getUserId(), codes.codes().get(0));
        TotpRecoveryResult reused = totpRecoveryService.start(user.getUserId(), codes.codes().get(0));

        assertThat(recovery.success()).isTrue();
        assertThat(recovery.enrollment().otpauthUri()).startsWith("otpauth://totp/");
        assertThat(reused.success()).isFalse();
    }

    @Test
    void disablesTotpAndInvalidatesAllRecoveryCodes() {
        UserIdentityEntity user = createUser("disable");
        TotpEnrollmentStart start = totpService.startEnrollment(user.getId());
        byte[] secret = secretFromUri(start.otpauthUri());
        char[] code = totpAlgorithm.generateCode(secret, clock.instant()).toCharArray();
        RecoveryCodeBatch recoveryCodes = totpService.confirmEnrollment(user.getId(), code)
            .recoveryCodes();

        totpService.disable(user.getId());

        assertThat(totpService.isEnrolled(user.getId())).isFalse();
        assertThat(recoveryCodeService.consume(user.getId(), recoveryCodes.codes().get(0)))
            .isFalse();
        Arrays.fill(code, '\0');
        Arrays.fill(secret, (byte) 0);
    }

    @Test
    void createsExpiresInvalidatesAndSuspendsSessions() {
        UserIdentityEntity user = createUser("session");
        String absoluteSession = "absolute-" + UUID.randomUUID();
        sessionService.create(
            absoluteSession, user.getId(), AuthenticationMethod.EMAIL_OTP, SessionScope.NORMAL
        );
        assertThat(sessionService.validateAndTouch(absoluteSession, SessionScope.NORMAL))
            .isEqualTo(SessionValidationStatus.VALID);
        jdbcTemplate.update(
            "UPDATE auth.user_session_metadata SET created_at=now()-interval '9 hours', "
                + "absolute_expires_at=now()-interval '1 second' "
                + "WHERE session_id=?",
            absoluteSession
        );
        assertThat(sessionService.validateAndTouch(absoluteSession, SessionScope.NORMAL))
            .isEqualTo(SessionValidationStatus.ABSOLUTE_EXPIRED);

        String invalidatedSession = "invalidated-" + UUID.randomUUID();
        sessionService.create(
            invalidatedSession, user.getId(), AuthenticationMethod.TOTP, SessionScope.NORMAL
        );
        sessionService.invalidate(invalidatedSession);
        assertThat(sessionService.validateAndTouch(invalidatedSession, SessionScope.NORMAL))
            .isEqualTo(SessionValidationStatus.INVALIDATED);

        String suspendedSession = "suspended-" + UUID.randomUUID();
        sessionService.create(
            suspendedSession, user.getId(), AuthenticationMethod.EMAIL_OTP, SessionScope.NORMAL
        );
        jdbcTemplate.update(
            "UPDATE auth.users SET account_status='SUSPENDED' WHERE id=?",
            user.getId()
        );
        entityManager.clear();
        assertThat(sessionService.validateAndTouch(suspendedSession, SessionScope.NORMAL))
            .isEqualTo(SessionValidationStatus.ACCOUNT_SUSPENDED);
    }

    @Test
    void enforcesCsrfSecurityHeadersAndRequestRateLimit() throws Exception {
        mvc.perform(get("/api/v1/csrf").secure(true))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Security-Policy",
                containsString("frame-ancestors 'none'")))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Strict-Transport-Security",
                containsString("max-age=31536000")));

        String userId = "limited." + suffix();
        String body = "{\"userId\":\"" + userId + "\"}";
        mvc.perform(post("/api/v1/login/email/send")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());

        for (int request = 0; request < 5; request++) {
            mvc.perform(post("/api/v1/login/email/send").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                    "입력 정보가 유효하다면 인증 메일이 발송되었습니다."));
        }
        mvc.perform(post("/api/v1/login/email/send").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.code").value("REQUEST_THROTTLED"));
    }

    @Test
    void usesIndistinguishableDecoyForUnknownLoginAndDefersSignupConflict() {
        UserIdentityEntity existing = createUser("enumeration");
        mailSender.clear();

        OtpRequestResult known = emailOtpService.sendLoginOtp(existing.getUserId());
        OtpRequestResult unknown = emailOtpService.sendLoginOtp("unknown." + suffix());

        assertThat(known.status()).isEqualTo(OtpVerificationStatus.SUCCESS);
        assertThat(unknown.status()).isEqualTo(OtpVerificationStatus.SUCCESS);
        assertThat(known.challengeId()).isNotNull();
        assertThat(unknown.challengeId()).isNotNull();
        assertThat(mailSender.messages()).hasSize(1);

        OtpRequestResult duplicateSignup = emailOtpService.startSignup(
            new CreateUserIdentityCommand(existing.getUserId(), "Duplicate Candidate",
                "candidate." + suffix() + "@example.com"));
        char[] code = capturedCode(duplicateSignup.challengeId());
        try {
            assertThat(emailOtpService.verifySignup(duplicateSignup.challengeId(), code).status())
                .isEqualTo(OtpVerificationStatus.IDENTITY_CONFLICT);
        } finally {
            Arrays.fill(code, '\0');
        }
    }

    private UserIdentityEntity createUser(String prefix) {
        String suffix = suffix();
        return userIdentityService.createIdentity(new CreateUserIdentityCommand(
            prefix + "." + suffix,
            "테스트 사용자",
            prefix + "." + suffix + "@example.com"
        ));
    }

    private char[] capturedCode(UUID challengeId) {
        return mailSender.messages().stream()
            .filter(message -> message.challengeId().equals(challengeId))
            .map(CapturedOtpMail::code)
            .findFirst()
            .orElseThrow()
            .toCharArray();
    }

    private char[] differentCode(char[] actual) {
        char[] candidate = actual.clone();
        candidate[0] = candidate[0] == '9' ? '0' : (char) (candidate[0] + 1);
        return candidate;
    }

    private byte[] secretFromUri(String uri) {
        String encoded = Arrays.stream(uri.substring(uri.indexOf('?') + 1).split("&"))
            .filter(value -> value.startsWith("secret="))
            .map(value -> value.substring("secret=".length()))
            .findFirst()
            .orElseThrow();
        return decodeBase32(encoded);
    }

    private byte[] decodeBase32(String value) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        List<Byte> bytes = new ArrayList<>();
        int buffer = 0;
        int bits = 0;
        for (char character : value.toUpperCase(Locale.ROOT).toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(character);
            bits += 5;
            if (bits >= 8) {
                bytes.add((byte) ((buffer >>> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        byte[] result = new byte[bytes.size()];
        for (int index = 0; index < bytes.size(); index++) {
            result[index] = bytes.get(index);
        }
        return result;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String testKey(byte fill) {
        byte[] key = new byte[32];
        Arrays.fill(key, fill);
        return Base64.getEncoder().encodeToString(key);
    }
}
