package com.ssolab.auth.identity.api;

import com.ssolab.auth.identity.service.UserSelfService;
import com.ssolab.auth.passwordless.session.PasswordlessPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/profile")
public class UserProfileController {

    private final UserSelfService users;

    public UserProfileController(UserSelfService users) {
        this.users = users;
    }

    @GetMapping
    ResponseEntity<UserProfileDtos.ProfileResponse> profile(
        @AuthenticationPrincipal PasswordlessPrincipal principal,
        HttpServletRequest request
    ) {
        return noStore(users.profile(principal.userId(), request.getSession().getId()));
    }

    @PatchMapping("/username")
    ResponseEntity<UserProfileDtos.ProfileResponse> changeUsername(
        @AuthenticationPrincipal PasswordlessPrincipal principal,
        @Valid @RequestBody UserProfileDtos.UsernameChangeRequest body,
        HttpServletRequest request
    ) {
        return noStore(users.changeUsername(
            principal.userId(),
            body.username(),
            request.getSession().getId(),
            UUID.randomUUID().toString()
        ));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(body);
    }
}
