package com.ssolab.auth.identity.api;

import com.ssolab.auth.oidc.logout.LogoutCoordinator;
import com.ssolab.auth.passwordless.api.ApiError;
import com.ssolab.auth.passwordless.session.AuthSessionService;
import com.ssolab.auth.passwordless.session.PasswordlessPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/account")
public class UserAccountController {

    private final AuthSessionService sessions;
    private final LogoutCoordinator logout;

    public UserAccountController(AuthSessionService sessions, LogoutCoordinator logout) {
        this.sessions = sessions;
        this.logout = logout;
    }

    @DeleteMapping
    ResponseEntity<?> delete(
        @AuthenticationPrincipal PasswordlessPrincipal principal,
        @Valid @RequestBody UserProfileDtos.AccountDeleteRequest body,
        HttpServletRequest request
    ) {
        HttpSession current = request.getSession(false);
        if (current == null || !sessions.hasFreshReauthentication(
            current.getId(), principal.userId()
        )) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ApiError("REAUTHENTICATION_REQUIRED", "재인증이 필요합니다.")
            );
        }
        logout.hardDelete(principal.userId(), UUID.randomUUID().toString());
        try {
            current.invalidate();
        } catch (IllegalStateException ignored) {
            // The backing Spring Session row was removed as part of Hard Delete.
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
