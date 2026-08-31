package com.ssolab.auth.identity.api;

import com.ssolab.auth.oidc.logout.LogoutCoordinator;
import com.ssolab.auth.passwordless.api.ApiError;
import com.ssolab.auth.passwordless.api.OtpRequestResponse;
import com.ssolab.auth.passwordless.emailchange.EmailChangeVerificationResult;
import com.ssolab.auth.passwordless.otp.OtpRequestResult;
import com.ssolab.auth.passwordless.otp.OtpVerificationStatus;
import com.ssolab.auth.passwordless.otp.PasswordlessEmailOtpService;
import com.ssolab.auth.passwordless.session.PasswordlessPrincipal;
import com.ssolab.auth.security.ratelimit.RateLimitAction;
import com.ssolab.auth.security.ratelimit.SecurityRateLimitService;
import com.ssolab.auth.security.ratelimit.SecurityRequestIdentifiers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/email-change")
public class UserEmailChangeController {

    private final PasswordlessEmailOtpService emailOtp;
    private final LogoutCoordinator logout;
    private final SecurityRateLimitService rateLimits;
    private final SecurityRequestIdentifiers identifiers;

    public UserEmailChangeController(
        PasswordlessEmailOtpService emailOtp,
        LogoutCoordinator logout,
        SecurityRateLimitService rateLimits,
        SecurityRequestIdentifiers identifiers
    ) {
        this.emailOtp = emailOtp;
        this.logout = logout;
        this.rateLimits = rateLimits;
        this.identifiers = identifiers;
    }

    @PostMapping("/start")
    OtpRequestResponse start(
        @AuthenticationPrincipal PasswordlessPrincipal principal,
        @Valid @RequestBody UserProfileDtos.EmailChangeStartRequest body,
        HttpServletRequest request
    ) {
        rateLimits.check(
            RateLimitAction.EMAIL_OTP_SEND,
            identifiers.remoteAddress(request),
            principal.userId() + ":" + identifiers.email(body.newEmail()),
            null
        );
        return OtpRequestResponse.from(emailOtp.startEmailChange(
            principal.userId(), body.newEmail()
        ));
    }

    @PostMapping("/{challengeId}/resend")
    ResponseEntity<?> resend(
        @AuthenticationPrincipal PasswordlessPrincipal principal,
        @PathVariable UUID challengeId,
        HttpServletRequest request
    ) {
        rateLimits.check(
            RateLimitAction.EMAIL_OTP_RESEND,
            identifiers.remoteAddress(request),
            principal.userId().toString(),
            challengeId.toString()
        );
        OtpRequestResult result = emailOtp.resendEmailChange(principal.userId(), challengeId);
        if (result.status() != OtpVerificationStatus.SUCCESS) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                new ApiError("OTP_REQUEST_REJECTED", "잠시 후 다시 시도해주세요.")
            );
        }
        return ResponseEntity.ok(OtpRequestResponse.from(result));
    }

    @PostMapping("/verify")
    ResponseEntity<?> verify(
        @AuthenticationPrincipal PasswordlessPrincipal principal,
        @Valid @RequestBody UserProfileDtos.EmailChangeVerifyRequest body,
        HttpServletRequest request
    ) {
        rateLimits.check(
            RateLimitAction.EMAIL_OTP_VERIFY,
            identifiers.remoteAddress(request),
            principal.userId().toString(),
            body.challengeId().toString()
        );
        char[] code = body.copyCode();
        try {
            EmailChangeVerificationResult result = emailOtp.verifyEmailChange(
                principal.userId(),
                body.challengeId(),
                code,
                UUID.randomUUID().toString()
            );
            if (result.status() != OtpVerificationStatus.SUCCESS) {
                return ResponseEntity.badRequest().body(
                    new ApiError("AUTHENTICATION_FAILED", "인증에 실패했습니다.")
                );
            }
            logout.revokeForEmailChange(
                principal.userId(), request.getSession().getId()
            );
            return ResponseEntity.ok(Map.of("changed", true));
        } finally {
            Arrays.fill(code, '\0');
        }
    }
}
