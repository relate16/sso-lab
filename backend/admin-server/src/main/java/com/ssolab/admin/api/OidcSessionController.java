package com.ssolab.admin.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ssolab.admin.logout.BffSessionRegistry;
import jakarta.servlet.http.HttpSession;

@RestController @RequestMapping("/api/v1")
public class OidcSessionController {
    private final BffSessionRegistry registry;
    public OidcSessionController(BffSessionRegistry registry) { this.registry = registry; }
    private static final List<String> EXPOSED_CLAIMS = List.of("sub", "userId", "preferred_username",
        "username", "name", "email", "roles", "groups", "amr", "acr", "auth_time", "sid");
    @GetMapping("/session") Map<String, Object> session(@AuthenticationPrincipal OidcUser user, HttpSession session) {
        registry.register(session, user);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("authenticated", true); response.put("service", "ADMIN");
        EXPOSED_CLAIMS.forEach(name -> { Object value = user.getClaims().get(name); if (value != null) response.put(name, value); });
        return response;
    }
}
