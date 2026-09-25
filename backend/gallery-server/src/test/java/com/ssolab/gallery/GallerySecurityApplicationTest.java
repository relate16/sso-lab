package com.ssolab.gallery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssolab.gallery.api.InquiryController;
import com.ssolab.gallery.api.PublicGalleryController;
import com.ssolab.gallery.api.StudioGalleryController;
import com.ssolab.gallery.artwork.PublicArtworkService;
import com.ssolab.gallery.artwork.StudioArtworkService;
import com.ssolab.gallery.config.GallerySecurityConfig;
import com.ssolab.gallery.inquiry.InquiryRateLimiter;
import com.ssolab.gallery.inquiry.InquiryService;
import com.ssolab.gallery.logout.BffSessionRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {PublicGalleryController.class, StudioGalleryController.class,
    InquiryController.class})
@Import({GallerySecurityConfig.class, BffSessionRegistry.class,
    GallerySecurityApplicationTest.SecurityTestConfiguration.class})
@TestPropertySource(properties = {
    "sso.oidc-client.registration-id=gallery-client",
    "sso.oidc-client.client-id=gallery-client",
    "sso.oidc-client.client-secret=gallery-test-secret-not-for-production",
    "sso.oidc-client.issuer=http://localhost:8080",
    "sso.oidc-client.authorization-uri=http://localhost:8080/oauth2/authorize",
    "sso.oidc-client.token-uri=http://localhost:8080/oauth2/token",
    "sso.oidc-client.jwk-set-uri=http://localhost:8080/oauth2/jwks",
    "sso.oidc-client.user-info-uri=http://localhost:8080/userinfo",
    "sso.oidc-client.redirect-uri=http://localhost/login/oauth2/code/gallery-client",
    "sso.oidc-client.post-logout-redirect-uri=http://localhost/"
})
class GallerySecurityApplicationTest {
    @EnableWebSecurity
    static class SecurityTestConfiguration {
    }

    @MockitoBean PublicArtworkService publicArtworks;
    @MockitoBean StudioArtworkService studioArtworks;
    @MockitoBean InquiryService inquiries;
    @MockitoBean InquiryRateLimiter rateLimiter;

    @Test
    void publicBrowsingIsAnonymousButStudioRequiresAdmin(@Autowired MockMvc mvc) throws Exception {
        when(publicArtworks.home()).thenReturn(List.of());
        when(studioArtworks.list()).thenReturn(List.of());
        mvc.perform(get("/api/v1/gallery/home")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/gallery/studio/artworks")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/v1/gallery/studio/artworks").with(oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/gallery/studio/artworks").with(oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk());
    }

    @Test
    void anonymousInquiryStillRequiresCsrf(@Autowired MockMvc mvc) throws Exception {
        when(inquiries.create(any())).thenReturn(UUID.randomUUID());
        String request = """
            {"artworkId":"11111111-1111-4111-8111-111111111111","name":"Visitor",
             "email":"visitor@example.test","phone":"","message":"Question","privacyAgreed":true}
            """;
        mvc.perform(post("/api/v1/gallery/inquiries").contentType("application/json").content(request))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/gallery/inquiries").with(csrf())
                .contentType("application/json").content(request))
            .andExpect(status().isCreated());
    }
}
