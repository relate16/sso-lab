package com.ssolab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ssolab.auth.identity.model.IdentityGroupEntity;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.service.CreateUserIdentityCommand;
import com.ssolab.auth.identity.service.GroupHierarchyService;
import com.ssolab.auth.identity.service.IdentityMembershipService;
import com.ssolab.auth.identity.service.UserIdentityService;
import com.ssolab.auth.passwordless.mail.CapturedOtpMail;
import com.ssolab.auth.passwordless.mail.InMemoryVerificationMailSender;
import com.ssolab.auth.passwordless.otp.OtpRequestResult;
import com.ssolab.auth.passwordless.otp.PasswordlessEmailOtpService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class OidcPostgresqlIntegrationTest {

    private static final String EMAIL_KEY = key((byte) 0x51);
    private static final String EMAIL_HMAC = key((byte) 0x52);
    private static final String OTP_HMAC = key((byte) 0x53);
    private static final String TOTP_KEY = key((byte) 0x54);

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
        registry.add("SESSION_COOKIE_SECURE", () -> "false");
        OidcTestProperties.register(registry);
    }

    @Autowired MockMvc mvc;
    @Autowired UserIdentityService userIdentityService;
    @Autowired GroupHierarchyService groupHierarchyService;
    @Autowired IdentityMembershipService membershipService;
    @Autowired PasswordlessEmailOtpService emailOtpService;
    @Autowired InMemoryVerificationMailSender mailSender;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearMail() {
        mailSender.clear();
    }

    @Test
    void completesAuthorizationCodePkceTokensUserInfoRefreshAndSso() throws Exception {
        UserIdentityEntity user = userIdentityService.createIdentity(
            new CreateUserIdentityCommand("oidc.user", "OIDC 사용자", "oidc.user@example.com")
        );
        IdentityGroupEntity root = groupHierarchyService.createGroup("개발본부", null);
        IdentityGroupEntity child = groupHierarchyService.createGroup("백엔드팀", root.getId());
        membershipService.assignGroup(user.getId(), child.getId());

        Cookie authSession = passwordlessLogin("oidc.user");

        String verifier = "hr-verifier-abcdefghijklmnopqrstuvwxyz-0123456789-ABCDE";
        String hrCode = authorize("hr-client",
            "http://localhost:8081/login/oauth2/code/hr-client", verifier, authSession);
        TokenResponse tokens = exchangeCode("hr-client", OidcTestProperties.HR_SECRET,
            "http://localhost:8081/login/oauth2/code/hr-client", hrCode, verifier, 200);

        Jwt idToken = jwtDecoder.decode(tokens.idToken());
        Jwt accessToken = jwtDecoder.decode(tokens.accessToken());
        assertThat(idToken.getIssuer().toString()).isEqualTo("http://localhost:8080");
        assertThat(idToken.getSubject()).isEqualTo(user.getId().toString());
        assertThat(idToken.getClaimAsString("preferred_username")).isEqualTo("oidc.user");
        assertThat(idToken.getClaimAsString("email")).isEqualTo("oidc.user@example.com");
        assertThat(idToken.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(idToken.getClaimAsStringList("groups")).containsExactly("/개발본부/백엔드팀");
        assertThat(idToken.getClaimAsStringList("amr")).containsExactly("email_otp");
        assertThat(idToken.getClaimAsString("acr")).isEqualTo("urn:jb:loa:1");
        assertThat(idToken.getClaimAsString("sid")).isNotBlank();
        assertThat(Duration.between(idToken.getIssuedAt(), idToken.getExpiresAt()))
            .isEqualTo(Duration.ofMinutes(5));
        assertThat(Duration.between(accessToken.getIssuedAt(), accessToken.getExpiresAt()))
            .isEqualTo(Duration.ofMinutes(5));

        MvcResult userInfo = mvc.perform(
                get("/userinfo").header("Authorization", "Bearer " + tokens.accessToken())
            )
            .andReturn();
        assertThat(userInfo.getResponse().getStatus())
            .withFailMessage("UserInfo failed: %s / %s",
                userInfo.getResponse().getHeader("WWW-Authenticate"),
                userInfo.getResponse().getContentAsString())
            .isEqualTo(200);
        jsonPath("$.sub").value(user.getId().toString()).match(userInfo);
        jsonPath("$.email").value("oidc.user@example.com").match(userInfo);
        jsonPath("$.acr").value("urn:jb:loa:1").match(userInfo);

        String approvalVerifier = "approval-verifier-abcdefghijklmnopqrstuvwxyz-0123456789";
        String approvalCode = authorize("approval-client",
            "http://localhost:8082/login/oauth2/code/approval-client",
            approvalVerifier, authSession);
        exchangeCode("approval-client", OidcTestProperties.APPROVAL_SECRET,
            "http://localhost:8082/login/oauth2/code/approval-client",
            approvalCode, approvalVerifier + "-wrong", 400);

        String approvalSuccessVerifier =
            "approval-success-verifier-abcdefghijklmnopqrstuvwxyz-0123456789";
        String approvalSuccessCode = authorize("approval-client",
            "http://localhost:8082/login/oauth2/code/approval-client",
            approvalSuccessVerifier, authSession);
        TokenResponse approvalTokens = exchangeCode(
            "approval-client", OidcTestProperties.APPROVAL_SECRET,
            "http://localhost:8082/login/oauth2/code/approval-client",
            approvalSuccessCode, approvalSuccessVerifier, 200);
        assertThat(approvalTokens.idToken()).isNotBlank();

        String adminVerifier =
            "admin-success-verifier-abcdefghijklmnopqrstuvwxyz-0123456789";
        String adminCode = authorize("admin-client",
            "http://localhost:8083/login/oauth2/code/admin-client",
            adminVerifier, authSession);
        TokenResponse adminTokens = exchangeCode(
            "admin-client", OidcTestProperties.ADMIN_SECRET,
            "http://localhost:8083/login/oauth2/code/admin-client",
            adminCode, adminVerifier, 200);
        assertThat(adminTokens.idToken()).isNotBlank();

        TokenResponse refreshed = refresh("hr-client", OidcTestProperties.HR_SECRET,
            tokens.refreshToken(), 200);
        assertThat(refreshed.accessToken()).isNotEqualTo(tokens.accessToken());
        assertThat(refreshed.refreshToken()).isNotEqualTo(tokens.refreshToken());
        refresh("hr-client", OidcTestProperties.HR_SECRET, tokens.refreshToken(), 400);

        String reuseVerifier = "reuse-verifier-abcdefghijklmnopqrstuvwxyz-0123456789";
        String reuseCode = authorize("hr-client",
            "http://localhost:8081/login/oauth2/code/hr-client",
            reuseVerifier, authSession);
        exchangeCode("hr-client", OidcTestProperties.HR_SECRET,
            "http://localhost:8081/login/oauth2/code/hr-client",
            reuseCode, reuseVerifier, 200);
        exchangeCode("hr-client", OidcTestProperties.HR_SECRET,
            "http://localhost:8081/login/oauth2/code/hr-client",
            reuseCode, reuseVerifier, 400);

        mvc.perform(get("/oauth2/authorize").cookie(authSession)
                .queryParam("response_type", "code").queryParam("client_id", "hr-client")
                .queryParam("scope", "openid profile email")
                .queryParam("redirect_uri", "http://attacker.invalid/callback")
                .queryParam("state", "invalid-redirect-state")
                .queryParam("nonce", "invalid-redirect-nonce")
                .queryParam("code_challenge", challenge(verifier))
                .queryParam("code_challenge_method", "S256"))
            .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.flyway_schema_history WHERE version='4'", Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.oauth2_registered_client", Integer.class
        )).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList(
            "SELECT client_secret FROM auth.oauth2_registered_client", String.class
        )).allMatch(secret -> secret.startsWith("{bcrypt}"))
            .doesNotContain(OidcTestProperties.HR_SECRET,
                OidcTestProperties.APPROVAL_SECRET, OidcTestProperties.ADMIN_SECRET);

        jdbcTemplate.update("UPDATE auth.users SET account_status='SUSPENDED' WHERE id=?", user.getId());
        mvc.perform(get("/oauth2/authorize").cookie(authSession)
                .queryParam("response_type", "code").queryParam("client_id", "hr-client")
                .queryParam("scope", "openid profile").queryParam("state", "suspended-state")
                .queryParam("nonce", "suspended-nonce")
                .queryParam("redirect_uri", "http://localhost:8081/login/oauth2/code/hr-client")
                .queryParam("code_challenge", challenge(verifier)).queryParam("code_challenge_method", "S256"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void enforcesRpGlobalAndOwnerScopedSessionLogout() throws Exception {
        UserIdentityEntity user = userIdentityService.createIdentity(
            new CreateUserIdentityCommand("logout.user", "Logout User", "logout.user@example.com"));
        UserIdentityEntity other = userIdentityService.createIdentity(
            new CreateUserIdentityCommand("logout.other", "Other User", "logout.other@example.com"));
        Cookie firstSession = passwordlessLogin("logout.user");
        Cookie secondSession = passwordlessLogin("logout.user");
        Cookie otherSession = passwordlessLogin("logout.other");

        String verifier = "logout-verifier-abcdefghijklmnopqrstuvwxyz-0123456789";
        TokenResponse tokens = exchangeCode("hr-client", OidcTestProperties.HR_SECRET,
            "http://localhost:8081/login/oauth2/code/hr-client",
            authorize("hr-client", "http://localhost:8081/login/oauth2/code/hr-client",
                verifier, firstSession), verifier, 200);
        String approvalVerifier = "logout-approval-verifier-abcdefghijklmnopqrstuvwxyz-0123456789";
        exchangeCode("approval-client", OidcTestProperties.APPROVAL_SECRET,
            "http://localhost:8082/login/oauth2/code/approval-client",
            authorize("approval-client", "http://localhost:8082/login/oauth2/code/approval-client",
                approvalVerifier, firstSession), approvalVerifier, 200);

        String ownBody = mvc.perform(get("/api/v1/me/sessions").cookie(firstSession))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].device").exists())
            .andReturn().getResponse().getContentAsString();
        assertThat(ownBody).doesNotContain(firstSession.getValue());
        assertThat(JsonPath.<List<String>>read(ownBody, "$[*].id")).hasSize(2);

        String otherBody = mvc.perform(get("/api/v1/me/sessions").cookie(otherSession))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String otherPublicId = JsonPath.read(otherBody, "$[0].id");
        mvc.perform(delete("/api/v1/me/sessions/{id}", otherPublicId)
                .cookie(firstSession).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/me/logout-all").cookie(firstSession))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/me/logout-all").cookie(firstSession).with(csrf()))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me/sessions").cookie(secondSession))
            .andExpect(status().isForbidden());
        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + tokens.accessToken()))
            .andExpect(status().isUnauthorized());
        refresh("hr-client", OidcTestProperties.HR_SECRET, tokens.refreshToken(), 400);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.oauth2_authorization WHERE principal_name=?",
            Integer.class, user.getId().toString())).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.flyway_schema_history WHERE version='6'", Integer.class))
            .isEqualTo(1);

        Cookie rpSession = passwordlessLogin("logout.other");
        String rpVerifier = "rp-logout-verifier-abcdefghijklmnopqrstuvwxyz-0123456789";
        TokenResponse rpTokens = exchangeCode("admin-client", OidcTestProperties.ADMIN_SECRET,
            "http://localhost:8083/login/oauth2/code/admin-client",
            authorize("admin-client", "http://localhost:8083/login/oauth2/code/admin-client",
                rpVerifier, rpSession), rpVerifier, 200);
        mvc.perform(get("/connect/logout").cookie(rpSession)
                .queryParam("id_token_hint", rpTokens.idToken())
                .queryParam("post_logout_redirect_uri", "http://attacker.invalid/"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/connect/logout").cookie(rpSession)
                .queryParam("id_token_hint", rpTokens.idToken())
                .queryParam("post_logout_redirect_uri", "http://localhost:8083/"))
            .andExpect(status().is3xxRedirection())
            .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                .isEqualTo("http://localhost:8083/"));
        refresh("admin-client", OidcTestProperties.ADMIN_SECRET, rpTokens.refreshToken(), 400);
    }

    @Test
    void preservesOidcContinuationWhenAnonymousSpaProbesProtectedApis() throws Exception {
        userIdentityService.createIdentity(new CreateUserIdentityCommand(
            "browser.flow", "Browser Flow", "browser.flow@example.com"));
        String verifier = "browser-flow-verifier-abcdefghijklmnopqrstuvwxyz-0123456789";

        MvcResult authorization = mvc.perform(get("/oauth2/authorize")
                .queryParam("response_type", "code").queryParam("client_id", "hr-client")
                .queryParam("scope", "openid profile email")
                .queryParam("redirect_uri", "http://localhost:8081/login/oauth2/code/hr-client")
                .queryParam("state", "browser-state").queryParam("nonce", "browser-nonce")
                .queryParam("code_challenge", challenge(verifier))
                .queryParam("code_challenge_method", "S256"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        Cookie anonymousSession = authorization.getResponse().getCookie("SESSION");
        assertThat(anonymousSession).isNotNull();

        mvc.perform(get("/api/v1/me/profile").cookie(anonymousSession))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me/sessions").cookie(anonymousSession))
            .andExpect(status().isForbidden());

        OtpRequestResult request = emailOtpService.sendLoginOtp("browser.flow");
        CapturedOtpMail mail = mailSender.messages().stream()
            .filter(candidate -> candidate.challengeId().equals(request.challengeId()))
            .findFirst().orElseThrow();
        String body = "{\"challengeId\":\"" + request.challengeId()
            + "\",\"code\":\"" + mail.code() + "\"}";
        mvc.perform(post("/api/v1/login/email/verify").cookie(anonymousSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.continuationPath")
                .value(org.hamcrest.Matchers.startsWith("/oauth2/authorize?")));
    }

    private Cookie passwordlessLogin(String userId) throws Exception {
        OtpRequestResult request = emailOtpService.sendLoginOtp(userId);
        CapturedOtpMail mail = mailSender.messages().stream()
            .filter(candidate -> candidate.challengeId().equals(request.challengeId()))
            .findFirst().orElseThrow();
        String body = "{\"challengeId\":\"" + request.challengeId()
            + "\",\"code\":\"" + mail.code() + "\"}";
        MvcResult result = mvc.perform(post("/api/v1/login/email/verify").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticationMethod").value("email_otp"))
            .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("HttpOnly");
        return session;
    }

    private String authorize(String clientId, String redirectUri, String verifier,
        Cookie session) throws Exception {
        MvcResult result = mvc.perform(get("/oauth2/authorize").cookie(session)
                .queryParam("response_type", "code").queryParam("client_id", clientId)
                .queryParam("scope", "openid profile email").queryParam("redirect_uri", redirectUri)
                .queryParam("state", "state-" + clientId).queryParam("nonce", "nonce-" + clientId)
                .queryParam("code_challenge", challenge(verifier)).queryParam("code_challenge_method", "S256"))
            .andExpect(status().is3xxRedirection()).andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(redirectUri);
        var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(query.getFirst("state")).isEqualTo("state-" + clientId);
        return query.getFirst("code");
    }

    private TokenResponse exchangeCode(String clientId, String secret, String redirectUri,
        String code, String verifier, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/oauth2/token").with(httpBasic(clientId, secret))
                .param("grant_type", "authorization_code").param("code", code)
                .param("redirect_uri", redirectUri).param("code_verifier", verifier))
            .andExpect(status().is(expectedStatus)).andReturn();
        if (expectedStatus != 200) return TokenResponse.empty();
        String body = result.getResponse().getContentAsString();
        return new TokenResponse(JsonPath.read(body, "$.access_token"),
            JsonPath.read(body, "$.id_token"), JsonPath.read(body, "$.refresh_token"));
    }

    private TokenResponse refresh(String clientId, String secret, String refreshToken,
        int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/oauth2/token").with(httpBasic(clientId, secret))
                .param("grant_type", "refresh_token").param("refresh_token", refreshToken))
            .andExpect(status().is(expectedStatus)).andReturn();
        if (expectedStatus != 200) return TokenResponse.empty();
        String body = result.getResponse().getContentAsString();
        return new TokenResponse(JsonPath.read(body, "$.access_token"),
            JsonPath.read(body, "$.id_token"), JsonPath.read(body, "$.refresh_token"));
    }

    private static String challenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static String key(byte value) {
        byte[] bytes = new byte[32]; java.util.Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private record TokenResponse(String accessToken, String idToken, String refreshToken) {
        static TokenResponse empty() { return new TokenResponse(null, null, null); }
    }
}
