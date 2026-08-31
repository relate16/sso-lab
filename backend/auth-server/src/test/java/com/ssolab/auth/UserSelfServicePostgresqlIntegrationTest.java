package com.ssolab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssolab.auth.admin.bootstrap.BootstrapAdminService;
import com.ssolab.auth.identity.model.IdentityGroupEntity;
import com.ssolab.auth.identity.model.RoleName;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.service.CreateUserIdentityCommand;
import com.ssolab.auth.identity.service.GroupHierarchyService;
import com.ssolab.auth.identity.service.IdentityMembershipService;
import com.ssolab.auth.identity.service.UserIdentityService;
import com.ssolab.auth.oidc.claims.OidcIdentityClaimsService;
import com.ssolab.auth.oidc.logout.LogoutCoordinator;
import com.ssolab.auth.passwordless.emailchange.EmailChangeVerificationResult;
import com.ssolab.auth.passwordless.mail.CapturedOtpMail;
import com.ssolab.auth.passwordless.mail.InMemoryVerificationMailSender;
import com.ssolab.auth.passwordless.mail.OtpMailPurpose;
import com.ssolab.auth.passwordless.otp.OtpRequestResult;
import com.ssolab.auth.passwordless.otp.OtpVerificationStatus;
import com.ssolab.auth.passwordless.otp.PasswordlessEmailOtpService;
import com.ssolab.auth.passwordless.session.AuthenticationMethod;
import com.ssolab.auth.passwordless.session.PasswordlessPrincipal;
import com.ssolab.auth.passwordless.session.SessionScope;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(OrderAnnotation.class)
class UserSelfServicePostgresqlIntegrationTest {

    private static final String BOOTSTRAP_EMAIL = "self.bootstrap@example.com";
    private static final String EMAIL_KEY = key((byte) 0x71);
    private static final String EMAIL_HMAC = key((byte) 0x72);
    private static final String OTP_HMAC = key((byte) 0x73);
    private static final String TOTP_KEY = key((byte) 0x74);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("SSO_EMAIL_ENCRYPTION_KEY", () -> EMAIL_KEY);
        registry.add("SSO_EMAIL_LOOKUP_HMAC_KEY", () -> EMAIL_HMAC);
        registry.add("SSO_OTP_HMAC_KEY", () -> OTP_HMAC);
        registry.add("SSO_TOTP_ENCRYPTION_KEY", () -> TOTP_KEY);
        registry.add("BOOTSTRAP_ADMIN_ENABLED", () -> "true");
        registry.add("BOOTSTRAP_ADMIN_EMAIL", () -> BOOTSTRAP_EMAIL);
        registry.add("SESSION_COOKIE_SECURE", () -> "false");
        OidcTestProperties.register(registry);
    }

    @Autowired MockMvc mvc;
    @Autowired UserIdentityService identities;
    @Autowired IdentityMembershipService memberships;
    @Autowired GroupHierarchyService groups;
    @Autowired PasswordlessEmailOtpService emailOtp;
    @Autowired InMemoryVerificationMailSender mail;
    @Autowired LogoutCoordinator logout;
    @Autowired OidcIdentityClaimsService oidcClaims;
    @Autowired BootstrapAdminService bootstrap;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearMail() {
        mail.clear();
    }

    @Test
    @Order(1)
    void hardDeletesAllPersonalStatePreservesAuditAndDoesNotRestoreBootstrap() throws Exception {
        assertV7Schema();
        UserIdentityEntity bootstrapAdmin = signup(
            "self.bootstrap", "Bootstrap Admin", BOOTSTRAP_EMAIL
        );
        Cookie current = login(bootstrapAdmin.getUserId());
        Cookie other = login(bootstrapAdmin.getUserId());

        mvc.perform(delete("/api/v1/me/account").cookie(current).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"DELETE\"}"))
            .andExpect(status().isConflict());
        assertThat(identities.findByUserId("self.bootstrap")).isPresent();

        UserIdentityEntity recoveryAdmin = identities.createIdentity(
            new CreateUserIdentityCommand(
                "recovery.admin", "Recovery Admin", "recovery.admin@example.com"
            )
        );
        memberships.assignRole(recoveryAdmin.getId(), RoleName.ADMIN);

        mvc.perform(delete("/api/v1/me/account").cookie(current).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"delete\"}"))
            .andExpect(status().isBadRequest());
        jdbc.update(
            "UPDATE auth.user_session_metadata SET reauthenticated_at="
                + "now()-interval '10 minutes' WHERE session_id=?",
            rawSessionId(current)
        );
        mvc.perform(delete("/api/v1/me/account").cookie(current).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"DELETE\"}"))
            .andExpect(status().isUnauthorized());

        emailOtp.startEmailChange(bootstrapAdmin.getId(), "deleted.pending@example.com");
        insertCredentialFixtures(bootstrapAdmin.getId(), rawSessionId(current));
        insertOauthFixture(
            bootstrapAdmin.getId(), rawSessionId(other), "admin-client", 'x'
        );
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.pending_registrations "
                + "WHERE normalized_user_id='self.bootstrap'",
            Integer.class
        )).isGreaterThan(0);

        reauthenticateByEmail(current, bootstrapAdmin.getId());
        mvc.perform(delete("/api/v1/me/account").cookie(current).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"DELETE\"}"))
            .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.users WHERE id=?", Integer.class, bootstrapAdmin.getId()
        )).isZero();
        assertDeletedTables(bootstrapAdmin.getId(), rawSessionId(current));
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.SPRING_SESSION WHERE SESSION_ID=?",
            Integer.class, rawSessionId(other)
        )).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.pending_registrations "
                + "WHERE normalized_user_id='self.bootstrap'",
            Integer.class
        )).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.audit_logs WHERE event_type='ACCOUNT_DELETED' "
                + "AND actor_id=? AND target_id=?",
            Integer.class, bootstrapAdmin.getId(), bootstrapAdmin.getId()
        )).isEqualTo(1);
        assertThat(identities.findByUserId(recoveryAdmin.getUserId())).isPresent();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='auth' "
                + "AND table_name='audit_logs' AND column_name IN "
                + "('email','user_id','username','otp','token')",
            Integer.class
        )).isZero();

        byte[] configuredHashBeforeRestart = jdbc.queryForObject(
            "SELECT email_lookup_hash FROM auth.bootstrap_admin_state WHERE singleton_id=1",
            byte[].class
        );
        bootstrap.initialize();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.users WHERE id=?", Integer.class, bootstrapAdmin.getId()
        )).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT email_lookup_hash FROM auth.bootstrap_admin_state WHERE singleton_id=1",
            byte[].class
        )).isEqualTo(configuredHashBeforeRestart);
    }

    @Test
    @Order(2)
    void returnsOnlyOwnProfileAndKeepsSessionWhenUsernameChanges() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserIdentityEntity user = identities.createIdentity(new CreateUserIdentityCommand(
            "profile." + suffix, "Before Name", "profile." + suffix + "@example.com"
        ));
        IdentityGroupEntity root = groups.createGroup("Profile Root " + suffix, null);
        IdentityGroupEntity child = groups.createGroup("Profile Child " + suffix, root.getId());
        memberships.assignGroup(user.getId(), child.getId());
        Cookie session = login(user.getUserId());

        mvc.perform(get("/api/v1/me/profile").cookie(session))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.userId").value(user.getUserId()))
            .andExpect(jsonPath("$.email").value("profile." + suffix + "@example.com"))
            .andExpect(jsonPath("$.groups[0]").value(
                "/Profile Root " + suffix + "/Profile Child " + suffix
            ))
            .andExpect(jsonPath("$.sessions[0].current").value(true))
            .andExpect(jsonPath("$.emailLookupHash").doesNotExist());
        mvc.perform(get("/api/v1/me/profile"))
            .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/me/profile/username").cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"After Name\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("After Name"));
        mvc.perform(get("/api/v1/me/profile").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("After Name"));

        PasswordlessPrincipal principal = new PasswordlessPrincipal(
            user.getId(), user.getUserId(), java.util.List.of("USER"), SessionScope.NORMAL,
            AuthenticationMethod.EMAIL_OTP, Instant.now(), rawSessionId(session)
        );
        assertThat(oidcClaims.load(principal, Set.of("openid", "profile", "email"))
            .values().get("username")).isEqualTo("After Name");
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.audit_logs WHERE event_type='USERNAME_CHANGED' "
                + "AND actor_id=? AND target_id=?",
            Integer.class, user.getId(), user.getId()
        )).isEqualTo(1);
    }

    @Test
    @Order(3)
    void changesEmailUsingLatestOtpKeepsCurrentAuthAndRevokesOtherSessionsAndOAuth()
        throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserIdentityEntity user = identities.createIdentity(new CreateUserIdentityCommand(
            "email." + suffix, "Email User", "email.old." + suffix + "@example.com"
        ));
        Cookie current = login(user.getUserId());
        Cookie other = login(user.getUserId());
        insertOauthFixtures(user.getId(), rawSessionId(current), rawSessionId(other));

        OtpRequestResult superseded = emailOtp.startEmailChange(
            user.getId(), "email.first." + suffix + "@example.com"
        );
        char[] supersededCode = captured(superseded.challengeId(), OtpMailPurpose.EMAIL_CHANGE)
            .code().toCharArray();
        OtpRequestResult latest = emailOtp.startEmailChange(
            user.getId(), "email.new." + suffix + "@example.com"
        );
        char[] latestCode = captured(latest.challengeId(), OtpMailPurpose.EMAIL_CHANGE)
            .code().toCharArray();
        try {
            assertThat(emailOtp.verifyEmailChange(
                user.getId(), superseded.challengeId(), supersededCode, UUID.randomUUID().toString()
            ).status()).isEqualTo(OtpVerificationStatus.ALREADY_USED);
            EmailChangeVerificationResult changed = emailOtp.verifyEmailChange(
                user.getId(), latest.challengeId(), latestCode, UUID.randomUUID().toString()
            );
            assertThat(changed.status()).isEqualTo(OtpVerificationStatus.SUCCESS);
            assertThat(emailOtp.verifyEmailChange(
                user.getId(), latest.challengeId(), latestCode, UUID.randomUUID().toString()
            ).status()).isEqualTo(OtpVerificationStatus.ALREADY_USED);
        } finally {
            Arrays.fill(supersededCode, '\0');
            Arrays.fill(latestCode, '\0');
        }

        logout.revokeForEmailChange(user.getId(), rawSessionId(current));

        assertThat(identities.findByEmail("email.old." + suffix + "@example.com")).isEmpty();
        assertThat(identities.findByEmail("email.new." + suffix + "@example.com")).isPresent();
        PasswordlessPrincipal principal = new PasswordlessPrincipal(
            user.getId(), user.getUserId(), java.util.List.of("USER"), SessionScope.NORMAL,
            AuthenticationMethod.EMAIL_OTP, Instant.now(), rawSessionId(current)
        );
        assertThat(oidcClaims.load(principal, Set.of("openid", "profile", "email"))
            .values().get("email")).isEqualTo("email.new." + suffix + "@example.com");
        mvc.perform(get("/api/v1/me/profile").cookie(current))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("email.new." + suffix + "@example.com"));
        mvc.perform(get("/api/v1/me/profile").cookie(other))
            .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.oauth2_authorization WHERE principal_name=?",
            Integer.class, user.getId().toString()
        )).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.oidc_client_sessions WHERE user_id=?",
            Integer.class, user.getId()
        )).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT invalidated_at IS NULL FROM auth.user_session_metadata WHERE session_id=?",
            Boolean.class, rawSessionId(current)
        )).isTrue();
        assertThat(jdbc.queryForObject(
            "SELECT invalidated_at IS NOT NULL FROM auth.user_session_metadata WHERE session_id=?",
            Boolean.class, rawSessionId(other)
        )).isTrue();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.SPRING_SESSION WHERE SESSION_ID=?",
            Integer.class, rawSessionId(other)
        )).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.audit_logs WHERE event_type='EMAIL_CHANGED' "
                + "AND actor_id=?",
            Integer.class, user.getId()
        )).isEqualTo(1);
    }

    private UserIdentityEntity signup(String userId, String username, String email) {
        OtpRequestResult request = emailOtp.startSignup(
            new CreateUserIdentityCommand(userId, username, email)
        );
        char[] code = captured(request.challengeId(), OtpMailPurpose.SIGNUP)
            .code().toCharArray();
        try {
            UUID id = emailOtp.verifySignup(request.challengeId(), code).userId();
            return identities.findByUserId(userId).filter(user -> user.getId().equals(id))
                .orElseThrow();
        } finally {
            Arrays.fill(code, '\0');
        }
    }

    private Cookie login(String userId) throws Exception {
        mail.clear();
        OtpRequestResult request = emailOtp.sendLoginOtp(userId);
        String code = captured(request.challengeId(), OtpMailPurpose.LOGIN).code();
        MvcResult result = mvc.perform(post("/api/v1/login/email/verify").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"" + request.challengeId()
                    + "\",\"code\":\"" + code + "\"}"))
            .andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private String rawSessionId(Cookie session) {
        return new String(
            Base64.getDecoder().decode(session.getValue()), StandardCharsets.UTF_8
        );
    }

    private void reauthenticateByEmail(Cookie session, UUID userId) throws Exception {
        mail.clear();
        String body = mvc.perform(post("/api/v1/me/reauth/email/start")
                .cookie(session).with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID challengeId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.challengeId"));
        String code = captured(challengeId, OtpMailPurpose.LOGIN).code();
        mvc.perform(post("/api/v1/me/reauth/verify").cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"EMAIL_OTP\",\"challengeId\":\""
                    + challengeId + "\",\"code\":\"" + code + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reauthenticated").value(true));
        assertThat(userId).isNotNull();
    }

    private CapturedOtpMail captured(UUID challengeId, OtpMailPurpose purpose) {
        return mail.messages().stream()
            .filter(message -> message.challengeId().equals(challengeId))
            .filter(message -> message.purpose() == purpose)
            .findFirst().orElseThrow();
    }

    private void insertOauthFixtures(UUID userId, String firstSession, String secondSession) {
        insertOauthFixture(userId, firstSession, "hr-client", 'h');
        insertOauthFixture(userId, firstSession, "approval-client", 'a');
        insertOauthFixture(userId, secondSession, "admin-client", 'm');
    }

    private void insertOauthFixture(UUID userId, String sessionId, String clientId, char sidChar) {
        String registeredId = jdbc.queryForObject(
            "SELECT id FROM auth.oauth2_registered_client WHERE client_id=?",
            String.class, clientId
        );
        String authorizationId = UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO auth.oauth2_authorization "
                + "(id,registered_client_id,principal_name,authorization_grant_type,"
                + "refresh_token_value,refresh_token_issued_at,refresh_token_expires_at) "
                + "VALUES (?,?,?,?,?,now(),now()+interval '1 hour')",
            authorizationId, registeredId, userId.toString(), "authorization_code",
            "test-refresh-" + UUID.randomUUID()
        );
        jdbc.update(
            "INSERT INTO auth.oidc_client_sessions "
                + "(id,auth_session_id,user_id,authorization_id,registered_client_id,client_id,"
                + "oidc_sid,backchannel_logout_uri,created_at,last_accessed_at) "
                + "VALUES (?,?,?,?,?,?,?,?,now(),now())",
            UUID.randomUUID(), sessionId, userId, authorizationId, registeredId, clientId,
            String.valueOf(sidChar).repeat(43),
            "http://127.0.0.1:9/internal/oidc/backchannel-logout"
        );
    }

    private void insertCredentialFixtures(UUID userId, String sessionId) {
        jdbc.update(
            "INSERT INTO auth.totp_credentials "
                + "(user_id,secret_ciphertext,secret_iv,secret_key_version,status,"
                + "created_at,activated_at,updated_at) VALUES (?,?,?,?,?,now(),now(),now())",
            userId, new byte[] {1, 2, 3}, new byte[12], 1, "ACTIVE"
        );
        jdbc.update(
            "INSERT INTO auth.recovery_codes "
                + "(id,user_id,batch_id,code_hash,created_at) VALUES (?,?,?,?,now())",
            UUID.randomUUID(), userId, UUID.randomUUID(), "test-argon2id-hash"
        );
        insertOauthFixture(userId, sessionId, "hr-client", 'd');
        String registeredId = jdbc.queryForObject(
            "SELECT id FROM auth.oauth2_registered_client WHERE client_id='hr-client'",
            String.class
        );
        jdbc.update(
            "INSERT INTO auth.oauth2_authorization_consent "
                + "(registered_client_id,principal_name,authorities) VALUES (?,?,?)",
            registeredId, userId.toString(), "openid,profile,email"
        );
        byte[] proofHash = new byte[32];
        Arrays.fill(proofHash, (byte) 1);
        jdbc.update(
            "INSERT INTO auth.admin_reauth_proofs "
                + "(id,actor_id,proof_hash,authentication_method,issued_at,expires_at) "
                + "VALUES (?,?,?,?,now(),now()+interval '5 minutes')",
            UUID.randomUUID(), userId, proofHash, "EMAIL_OTP"
        );
    }

    private void assertDeletedTables(UUID userId, String sessionId) {
        String[] userTables = {
            "user_roles", "user_groups", "totp_credentials", "recovery_codes",
            "email_otp_challenges", "pending_email_changes", "user_session_metadata",
            "oidc_client_sessions"
        };
        for (String table : userTables) {
            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth." + table + " WHERE user_id=?",
                Integer.class, userId
            )).as(table).isZero();
        }
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.admin_reauth_proofs WHERE actor_id=?",
            Integer.class, userId
        )).as("admin_reauth_proofs").isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.oauth2_authorization WHERE principal_name=?",
            Integer.class, userId.toString()
        )).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.oauth2_authorization_consent WHERE principal_name=?",
            Integer.class, userId.toString()
        )).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.SPRING_SESSION WHERE SESSION_ID=?",
            Integer.class, sessionId
        )).isZero();
    }

    private void assertV7Schema() {
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.flyway_schema_history "
                + "WHERE version='7' AND success=true",
            Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForList(
            "SELECT constraint_name FROM information_schema.referential_constraints "
                + "WHERE constraint_schema='auth' AND delete_rule='CASCADE' "
                + "AND constraint_name IN "
                + "('pending_email_changes_user_fk',"
                + "'email_otp_challenges_pending_email_change_fk')",
            String.class
        )).containsExactlyInAnyOrder(
            "pending_email_changes_user_fk",
            "email_otp_challenges_pending_email_change_fk"
        );
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema='auth' AND table_name='pending_email_changes' "
                + "AND column_name IN ('email','new_email','plaintext_email')",
            Integer.class
        )).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.table_constraints "
                + "WHERE constraint_schema='auth' AND table_name='audit_logs' "
                + "AND constraint_type='FOREIGN KEY'",
            Integer.class
        )).isZero();
    }

    private static String key(byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
