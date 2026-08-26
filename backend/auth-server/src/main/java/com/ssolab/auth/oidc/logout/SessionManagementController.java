package com.ssolab.auth.oidc.logout;

import com.ssolab.auth.passwordless.session.PasswordlessPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class SessionManagementController {
    private final SessionManagementService sessions;
    private final LogoutCoordinator logout;

    public SessionManagementController(SessionManagementService sessions,
        LogoutCoordinator logout) {
        this.sessions = sessions; this.logout = logout;
    }

    @GetMapping("/sessions")
    List<SessionView> list(@AuthenticationPrincipal PasswordlessPrincipal principal,
        HttpServletRequest request) {
        return sessions.list(principal.userId(), request.getSession().getId());
    }

    @DeleteMapping("/sessions/{sessionId}")
    ResponseEntity<Void> terminate(@PathVariable UUID sessionId,
        @AuthenticationPrincipal PasswordlessPrincipal principal,
        HttpServletRequest request) {
        HttpSession current = request.getSession(false);
        String currentId = current == null ? null : current.getId();
        try {
            logout.logoutByPublicId(principal.userId(), sessionId, currentId);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
        boolean terminatedCurrent = sessions.list(principal.userId(), currentId).stream()
            .noneMatch(view -> view.current());
        if (terminatedCurrent && current != null) {
            current.invalidate();
            SecurityContextHolder.clearContext();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    ResponseEntity<Void> logoutAll(@AuthenticationPrincipal PasswordlessPrincipal principal,
        HttpServletRequest request) {
        HttpSession current = request.getSession(false);
        String currentId = current == null ? null : current.getId();
        logout.logoutAll(principal.userId(), currentId);
        if (current != null) current.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
