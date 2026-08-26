package com.ssolab.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.ssolab.admin.internal.AdminApiDtos;
import com.ssolab.admin.internal.InternalAdminClient;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@SpringBootTest(properties = {
    "ADMIN_CLIENT_SECRET=admin-test-secret-not-for-production",
    "ADMIN_INTERNAL_API_SECRET=internal-admin-test-secret-not-for-production",
    "AUTH_PUBLIC_URL=http://localhost:8080",
    "AUTH_INTERNAL_URL=http://localhost:8080",
    "ADMIN_REDIRECT_URI=http://localhost/login/oauth2/code/admin-client",
    "ADMIN_POST_LOGOUT_REDIRECT_URI=http://localhost:8083/",
    "ADMIN_INTERNAL_URL=http://localhost:8080/internal/admin/v1",
    "SESSION_COOKIE_SECURE=false"
})
@AutoConfigureMockMvc
class AdminServerApplicationTest {

    @MockitoBean
    InternalAdminClient internalAdminClient;

    @Test
    void reportsHealthy(@Autowired HealthEndpoint healthEndpoint) {
        assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void writesSecurityHeadersForHttps(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/actuator/health").secure(true)).andExpect(status().isOk())
            .andExpect(header().string("Content-Security-Policy",
                containsString("frame-ancestors 'none'")))
            .andExpect(header().string("Strict-Transport-Security",
                containsString("max-age=31536000")))
            .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void startsAuthorizationCodeFlowWithPkce(@Autowired MockMvc mvc) throws Exception {
        String location = mvc.perform(get("/oauth2/authorization/admin-client"))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        var parameters = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(parameters.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(parameters.getFirst("code_challenge")).isNotBlank();
    }

    @Test
    void initiatesExactRpLogoutAndRequiresCsrf(@Autowired MockMvc mvc,
        @Autowired ClientRegistrationRepository registrations) throws Exception {
        var registration = registrations.findByRegistrationId("admin-client");
        var login = oidcLogin().clientRegistration(registration)
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
            .idToken(token -> token.subject("00000000-0000-0000-0000-000000000001")
                .claim("roles", java.util.List.of("ADMIN")).claim("acr", "urn:jb:loa:1")
                .claim("sid", "test-oidc-session"));
        mvc.perform(post("/api/v1/logout").with(login)).andExpect(status().isForbidden());
        String location = mvc.perform(post("/api/v1/logout")
                .with(oidcLogin().clientRegistration(registration)
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                    .idToken(token -> token.subject("00000000-0000-0000-0000-000000000001")
                        .claim("roles", java.util.List.of("ADMIN")).claim("acr", "urn:jb:loa:1")
                        .claim("sid", "test-oidc-session"))).with(csrf()))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(location).startsWith("http://localhost:8080/connect/logout");
        assertThat(query.getFirst("post_logout_redirect_uri")).isEqualTo("http://localhost:8083/");
        assertThat(query.getFirst("id_token_hint")).isNotBlank();
    }

    @Test
    void requiresAdminRoleForAdminSession(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/api/v1/session").with(oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/session").with(oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .idToken(token -> token.subject("00000000-0000-0000-0000-000000000001")
                    .claim("roles", java.util.List.of("ADMIN"))
                    .claim("acr", "urn:jb:loa:1").claim("sid", "test-oidc-session"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.service").value("ADMIN"));
    }

    @Test
    void requiresLoaOneAndCsrfForAdminMutation(@Autowired MockMvc mvc) throws Exception {
        UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        mvc.perform(get("/api/v1/admin/users").with(adminLogin(actorId, "urn:jb:loa:0")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/users/{id}/suspend", targetId)
                .with(adminLogin(actorId, "urn:jb:loa:1")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/users/{id}/suspend", targetId)
                .with(adminLogin(actorId, "urn:jb:loa:1")).with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    void storesReauthProofOnlyInServerSession(@Autowired MockMvc mvc) throws Exception {
        UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(internalAdminClient.verifyReauth(any(), any(), anyString()))
            .thenReturn(new AdminApiDtos.InternalProofResponse(
                "opaque-internal-proof", Instant.now().plusSeconds(300)
            ));

        String response = mvc.perform(post("/api/v1/admin/reauth/verify")
                .with(adminLogin(actorId, "urn:jb:loa:1")).with(csrf())
                .contentType("application/json")
                .content("{\"method\":\"TOTP\",\"code\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.elevated").value(true))
            .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("opaque-internal-proof");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminLogin(
        UUID actorId,
        String acr
    ) {
        return oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
            .idToken(token -> token.subject(actorId.toString())
                .claim("roles", java.util.List.of("ADMIN"))
                .claim("acr", acr).claim("sid", "test-oidc-session"));
    }
}
