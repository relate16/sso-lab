package com.ssolab.auth.identity.api;

import com.ssolab.auth.passwordless.api.ApiError;
import com.ssolab.auth.passwordless.api.OtpRequestResponse;
import com.ssolab.auth.passwordless.otp.LoginVerificationResult;
import com.ssolab.auth.passwordless.otp.OtpVerificationStatus;
import com.ssolab.auth.passwordless.otp.PasswordlessEmailOtpService;
import com.ssolab.auth.passwordless.session.AuthSessionService;
import com.ssolab.auth.passwordless.session.PasswordlessPrincipal;
import com.ssolab.auth.passwordless.totp.TotpService;
import com.ssolab.auth.passwordless.totp.TotpVerificationResult;
import com.ssolab.auth.passwordless.totp.TotpVerificationStatus;
import com.ssolab.auth.security.ratelimit.RateLimitAction;
import com.ssolab.auth.security.ratelimit.SecurityRateLimitService;
import com.ssolab.auth.security.ratelimit.SecurityRequestIdentifiers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Arrays;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/reauth")
public class UserSelfReauthenticationController {

    private final PasswordlessEmailOtpService emailOtp;
    private final TotpService totp;
    private final AuthSessionService sessions;
    private final SecurityRateLimitService rateLimits;
    private final SecurityRequestIdentifiers identifiers;

    public UserSelfReauthenticationController(
        PasswordlessEmailOtpService emailOtp,
        TotpService totp,
        AuthSessionService sessions,
        SecurityRateLimitService rateLimits,
        SecurityRequestIdentifiers identifiers
    ) {
        this.emailOtp = emailOtp;
        this.totp = totp;
        this.sessions = sessions;
        this.rateLimits = rateLimits;
        this.identifiers = identifiers;
    }

    @PostMapping("/email/start")
    OtpRequestResponse startEmail(
        @AuthenticationPrincipal PasswordlessPrincipal principal,
        HttpServletRequest request
    ) {
        rateLimits.check(
            RateLimitAction.EMAIL_OTP_SEND,
            identifiers.remoteAddress(request),
            principal.userId().toString(),
            null
        );
        return OtpRequestResponse.from(emailOtp.sendLoginOtp(principal.loginId()));
    }

    @PostMapping("/verify")
    ResponseEntity<?> verify(
        @AuthenticationPrincipal PasswordlessPrincipal principal,
        @Valid @RequestBody UserProfileDtos.SelfReauthVerifyRequest body,
        HttpServletRequest request
    ) {
        char[] code = body.copyCode();
        try {
            boolean verified;
            if ("EMAIL_OTP".equals(body.method())) {
                if (body.challengeId() == null) {
                    return failed();
                }
                rateLimits.check(
                    RateLimitAction.EMAIL_OTP_VERIFY,
                    identifiers.remoteAddress(request),
                    principal.userId().toString(),
                    body.challengeId().toString()
                );
                LoginVerificationResult result = emailOtp.verifyLogin(body.challengeId(), code);
                verified = result.status() == OtpVerificationStatus.SUCCESS
                    && principal.userId().equals(result.userId());
            } else if ("TOTP".equals(body.method())) {
                rateLimits.check(
                    RateLimitAction.TOTP_VERIFY,
                    identifiers.remoteAddress(request),
                    principal.userId().toString(),
                    null
                );
                TotpVerificationResult result = totp.verifyLogin(principal.loginId(), code);
                verified = result.status() == TotpVerificationStatus.SUCCESS
                    && principal.userId().equals(result.userId());
            } else {
                return failed();
            }
            if (!verified) {
                return failed();
            }
            Instant expiresAt = sessions.markReauthenticated(
                request.getSession().getId(), principal.userId()
            );
            return ResponseEntity.ok(
                new UserProfileDtos.SelfReauthResponse(true, expiresAt)
            );
        } finally {
            Arrays.fill(code, '\0');
        }
    }

    private ResponseEntity<ApiError> failed() {
        return ResponseEntity.badRequest().body(
            new ApiError("AUTHENTICATION_FAILED", "인증에 실패했습니다.")
        );
    }
}
