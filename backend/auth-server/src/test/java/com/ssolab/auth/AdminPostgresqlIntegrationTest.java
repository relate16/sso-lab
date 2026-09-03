package com.ssolab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.service.CreateUserIdentityCommand;
import com.ssolab.auth.identity.service.UserIdentityService;
import com.ssolab.auth.passwordless.mail.CapturedOtpMail;
import com.ssolab.auth.passwordless.mail.InMemoryVerificationMailSender;
import com.ssolab.auth.passwordless.mail.OtpMailPurpose;
import com.ssolab.auth.passwordless.otp.OtpRequestResult;
import com.ssolab.auth.passwordless.otp.PasswordlessEmailOtpService;
import com.ssolab.auth.passwordless.session.AuthSessionService;
import com.ssolab.auth.passwordless.session.AuthenticationMethod;
import com.ssolab.auth.passwordless.session.SessionScope;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
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
class AdminPostgresqlIntegrationTest {

    private static final String BOOTSTRAP_EMAIL = "bootstrap.admin@example.com";
    private static final String EMAIL_KEY = key((byte) 0x61);
    private static final String EMAIL_HMAC = key((byte) 0x62);
    private static final String OTP_HMAC = key((byte) 0x63);
    private static final String TOTP_KEY = key((byte) 0x64);

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
    @Autowired PasswordlessEmailOtpService emailOtpService;
    @Autowired InMemoryVerificationMailSender mailSender;
    @Autowired UserIdentityService identityService;
    @Autowired AuthSessionService authSessionService;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void enforcesAdminBoundaryBootstrapManagementReauthAndAudit() throws Exception {
        UUID adminId = bootstrapAdmin();
        UUID laterSignupId = signup(
            "post.bootstrap", "Bootstrap 이후 가입자", "post.bootstrap@example.com"
        );
        UserIdentityEntity managed = identityService.createIdentity(new CreateUserIdentityCommand(
            "managed.user", "관리 대상", "managed.user@example.com"
        ));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.flyway_schema_history WHERE version='5'", Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.bootstrap_admin_state WHERE claimed_by=?", Integer.class,
            adminId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.user_roles ur JOIN auth.roles r ON r.id=ur.role_id "
                + "WHERE ur.user_id=? AND r.name='ADMIN'", Integer.class, adminId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.user_roles ur JOIN auth.roles r ON r.id=ur.role_id "
                + "WHERE ur.user_id=? AND r.name='USER'", Integer.class, adminId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.user_roles ur JOIN auth.roles r ON r.id=ur.role_id "
                + "WHERE ur.user_id=? AND r.name='USER'", Integer.class, laterSignupId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.user_roles ur JOIN auth.roles r ON r.id=ur.role_id "
                + "WHERE ur.user_id=? AND r.name='ADMIN'", Integer.class, laterSignupId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.bootstrap_admin_state "
                + "WHERE singleton_id=1 AND claimed_at IS NOT NULL AND claimed_by=?",
            Integer.class, adminId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.user_roles ur JOIN auth.roles r ON r.id=ur.role_id "
                + "WHERE r.name='ADMIN'", Integer.class
        )).isEqualTo(1);

        mvc.perform(get("/internal/admin/v1/users"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/admin/v1/users")
                .header("X-Internal-Client-Id", "admin-server")
                .header("X-Internal-Service-Secret", "wrong-secret")
                .header("X-Admin-Actor-Id", adminId))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/admin/v1/users")
                .headers(internalHeaders(adminId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.userId == 'managed.user')].maskedEmail")
                .value(org.hamcrest.Matchers.contains("m***@example.com")))
            .andExpect(jsonPath("$.content[?(@.userId == 'managed.user')].email")
                .doesNotExist());

        String groupBody = mvc.perform(post("/internal/admin/v1/groups")
                .headers(internalHeaders(adminId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Engineering\",\"parentId\":null}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID groupId = UUID.fromString(JsonPath.read(groupBody, "$.id"));
        mvc.perform(put("/internal/admin/v1/users/{id}/groups", managed.getId())
                .headers(internalHeaders(adminId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupIds\":[\"" + groupId + "\"]}"))
            .andExpect(status().isOk());
        mvc.perform(post("/internal/admin/v1/users/{id}/suspend", adminId)
                .headers(internalHeaders(adminId)))
            .andExpect(status().isConflict());
        mvc.perform(put("/internal/admin/v1/users/{id}/roles", managed.getId())
                .headers(internalHeaders(adminId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"USER\",\"ADMIN\"]}"))
            .andExpect(status().isOk());

        String managedSession = "admin-managed-" + UUID.randomUUID();
        authSessionService.create(
            managedSession, managed.getId(), AuthenticationMethod.EMAIL_OTP, SessionScope.NORMAL
        );
        mvc.perform(post("/internal/admin/v1/users/{id}/suspend", managed.getId())
                .headers(internalHeaders(adminId)))
            .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT account_status FROM auth.users WHERE id=?", String.class, managed.getId()
        )).isEqualTo("SUSPENDED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT invalidated_at IS NOT NULL FROM auth.user_session_metadata WHERE session_id=?",
            Boolean.class,
            managedSession
        )).isTrue();
        mvc.perform(post("/internal/admin/v1/users/{id}/resume", managed.getId())
                .headers(internalHeaders(adminId)))
            .andExpect(status().isOk());

        mailSender.clear();
        String startBody = mvc.perform(post("/internal/admin/v1/reauth/email/start")
                .headers(internalHeaders(adminId)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID challengeId = UUID.fromString(JsonPath.read(startBody, "$.challengeId"));
        String code = captured(challengeId, OtpMailPurpose.ADMIN_REAUTH).code();
        String proofBody = mvc.perform(post("/internal/admin/v1/reauth/verify")
                .headers(internalHeaders(adminId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"EMAIL_OTP\",\"challengeId\":\"" + challengeId
                    + "\",\"code\":\"" + code + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String proof = JsonPath.read(proofBody, "$.proof");
        assertThat(proof).hasSizeGreaterThanOrEqualTo(40).doesNotContain(code);

        mvc.perform(post("/internal/admin/v1/users/{id}/email/reveal", managed.getId())
                .headers(internalHeaders(adminId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"proof\":\"" + proof + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("managed.user@example.com"));
        jdbcTemplate.update(
            "UPDATE auth.admin_reauth_proofs SET issued_at=now()-interval '10 minutes', "
                + "expires_at=now()-interval '1 second' "
                + "WHERE proof_hash IS NOT NULL"
        );
        mvc.perform(post("/internal/admin/v1/users/{id}/email/reveal", managed.getId())
                .headers(internalHeaders(adminId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"proof\":\"" + proof + "\"}"))
            .andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForList(
            "SELECT event_type FROM auth.audit_logs", String.class
        )).contains("BOOTSTRAP_ADMIN_CLAIMED", "GROUP_CREATED", "GROUP_ASSIGNED",
            "ROLE_ASSIGNED", "ACCOUNT_SUSPENDED", "ACCOUNT_RESUMED",
            "ADMIN_REAUTH_SUCCESS", "ADMIN_EMAIL_REVEALED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='auth' "
                + "AND table_name IN ('bootstrap_admin_state','admin_reauth_proofs','audit_logs') "
                + "AND column_name IN ('email','otp','code','proof')", Integer.class
        )).isZero();
    }

    private UUID bootstrapAdmin() {
        return signup("bootstrap.admin", "초기 관리자", BOOTSTRAP_EMAIL);
    }

    private UUID signup(String userId, String username, String email) {
        mailSender.clear();
        OtpRequestResult request = emailOtpService.startSignup(new CreateUserIdentityCommand(
            userId, username, email
        ));
        char[] code = captured(request.challengeId(), OtpMailPurpose.SIGNUP).code().toCharArray();
        try {
            return emailOtpService.verifySignup(request.challengeId(), code).userId();
        } finally {
            Arrays.fill(code, '\0');
        }
    }

    private CapturedOtpMail captured(UUID challengeId, OtpMailPurpose purpose) {
        return mailSender.messages().stream()
            .filter(message -> message.challengeId().equals(challengeId))
            .filter(message -> message.purpose() == purpose)
            .findFirst().orElseThrow();
    }

    private org.springframework.http.HttpHeaders internalHeaders(UUID actorId) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-Internal-Client-Id", "admin-server");
        headers.add("X-Internal-Service-Secret", OidcTestProperties.INTERNAL_ADMIN_SECRET);
        headers.add("X-Admin-Actor-Id", actorId.toString());
        headers.add("X-Trace-Id", UUID.randomUUID().toString());
        return headers;
    }

    private static String key(byte value) {
        byte[] bytes = new byte[32]; Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
