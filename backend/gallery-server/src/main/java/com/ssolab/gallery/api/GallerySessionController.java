package com.ssolab.gallery.api;

import com.ssolab.gallery.logout.BffSessionRegistry;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GallerySessionController {
    private final BffSessionRegistry registry;
    public GallerySessionController(BffSessionRegistry registry) { this.registry = registry; }
    @GetMapping("/session")
    Map<String, Object> session(@AuthenticationPrincipal OidcUser user, HttpSession session) {
        if (user == null) return Map.of("authenticated", false, "service", "GALLERY");
        registry.register(session, user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authenticated", true); result.put("service", "GALLERY");
        result.put("name", user.getFullName()); result.put("preferred_username", user.getPreferredUsername());
        result.put("roles", user.getClaimAsStringList("roles"));
        return result;
    }
}
