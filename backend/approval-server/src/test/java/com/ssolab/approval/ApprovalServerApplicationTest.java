package com.ssolab.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(properties = {
    "APPROVAL_CLIENT_SECRET=approval-test-secret-not-for-production",
    "AUTH_PUBLIC_URL=http://localhost:8080",
    "AUTH_INTERNAL_URL=http://localhost:8080",
    "APPROVAL_REDIRECT_URI=http://localhost/login/oauth2/code/approval-client",
    "APPROVAL_POST_LOGOUT_REDIRECT_URI=http://localhost:8082/",
    "SESSION_COOKIE_SECURE=false"
})
@AutoConfigureMockMvc
class ApprovalServerApplicationTest {

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
        String location = mvc.perform(get("/oauth2/authorization/approval-client"))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        var parameters = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(parameters.getFirst("response_type")).isEqualTo("code");
        assertThat(parameters.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(parameters.getFirst("code_challenge")).isNotBlank();
        assertThat(parameters.getFirst("redirect_uri"))
            .isEqualTo("http://localhost/login/oauth2/code/approval-client");
    }

    @Test
    void initiatesExactRpLogoutAndRequiresCsrf(@Autowired MockMvc mvc,
        @Autowired ClientRegistrationRepository registrations) throws Exception {
        var registration = registrations.findByRegistrationId("approval-client");
        mvc.perform(post("/api/v1/logout").with(oidcLogin()
                .clientRegistration(registration).idToken(token -> token.claim("sid", "sid-approval"))))
            .andExpect(status().isForbidden());
        String location = mvc.perform(post("/api/v1/logout").with(oidcLogin()
                .clientRegistration(registration).idToken(token -> token.claim("sid", "sid-approval"))).with(csrf()))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(location).startsWith("http://localhost:8080/connect/logout");
        assertThat(query.getFirst("post_logout_redirect_uri")).isEqualTo("http://localhost:8082/");
        assertThat(query.getFirst("id_token_hint")).isNotBlank();
    }
}
