package com.ssolab.hr.logout;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BackChannelLogoutController {
    private final BackChannelLogoutService service;
    public BackChannelLogoutController(BackChannelLogoutService service) { this.service = service; }

    @PostMapping(path = "/internal/oidc/backchannel-logout",
        consumes = "application/x-www-form-urlencoded")
    ResponseEntity<?> logout(@RequestParam("logout_token") String token) {
        try {
            service.logout(token);
            return ResponseEntity.ok(Map.of("loggedOut", true));
        } catch (JwtException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_logout_token"));
        }
    }
}
