package com.ssolab.gallery.api;

import com.ssolab.gallery.analytics.ArtworkAnalyticsDtos;
import com.ssolab.gallery.analytics.ArtworkViewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(GalleryRoutes.PUBLIC_API + "/artworks")
public class ArtworkViewController {
    private static final Logger log = LoggerFactory.getLogger(ArtworkViewController.class);
    private final ArtworkViewService views;

    public ArtworkViewController(ArtworkViewService views) {
        this.views = views;
    }

    @PostMapping("/{publicId}/views")
    public ResponseEntity<Void> view(@PathVariable UUID publicId,
        @Valid @RequestBody ArtworkAnalyticsDtos.ViewRequest body,
        @CookieValue(name = ArtworkViewService.VISITOR_COOKIE, required = false) String visitor,
        Authentication authentication, HttpServletRequest request) {
        if (isAdministrator(authentication) || views.isBot(request.getHeader("User-Agent"))) {
            return ResponseEntity.noContent().build();
        }

        boolean created = visitor == null || !visitor.matches("^[A-Za-z0-9_-]{43}$");
        String token = created ? views.newVisitorToken() : visitor;
        try {
            views.record(publicId, body, token, request.getHeader("Referer"),
                request.getServerName());
        } catch (RuntimeException exception) {
            log.warn("Artwork view recording failed for {}", publicId, exception);
        }

        var response = ResponseEntity.status(HttpStatus.NO_CONTENT);
        if (created) {
            ResponseCookie cookie = ResponseCookie.from(ArtworkViewService.VISITOR_COOKIE, token)
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/")
                .maxAge(Duration.ofSeconds(views.cookieMaxAgeSeconds())).build();
            response.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return response.build();
    }

    private boolean isAdministrator(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
            && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
