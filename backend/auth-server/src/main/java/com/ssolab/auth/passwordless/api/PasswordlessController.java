package com.ssolab.auth.passwordless.api;

import com.ssolab.auth.passwordless.otp.LoginVerificationResult;
import com.ssolab.auth.oidc.flow.OidcContinuationService;
import com.ssolab.auth.oidc.logout.LogoutCoordinator;
import com.ssolab.auth.passwordless.otp.OtpRequestResult;
import com.ssolab.auth.passwordless.otp.OtpVerificationStatus;
import com.ssolab.auth.passwordless.otp.PasswordlessEmailOtpService;
import com.ssolab.auth.passwordless.otp.SignupVerificationResult;
import com.ssolab.auth.passwordless.recovery.RecoveryCodeBatch;
import com.ssolab.auth.passwordless.recovery.RecoveryCodeService;
import com.ssolab.auth.passwordless.recovery.TotpRecoveryResult;
import com.ssolab.auth.passwordless.recovery.TotpRecoveryService;
import com.ssolab.auth.passwordless.session.AuthenticationMethod;
import com.ssolab.auth.passwordless.session.AuthSessionService;
import com.ssolab.auth.passwordless.session.PasswordlessPrincipal;
import com.ssolab.auth.passwordless.session.PasswordlessSessionAuthenticationService;
import com.ssolab.auth.passwordless.session.SessionScope;
import com.ssolab.auth.passwordless.totp.TotpEnrollmentResult;
import com.ssolab.auth.passwordless.totp.TotpEnrollmentStart;
import com.ssolab.auth.passwordless.totp.TotpService;
import com.ssolab.auth.passwordless.totp.TotpVerificationResult;
import com.ssolab.auth.passwordless.totp.TotpVerificationStatus;
import com.ssolab.auth.security.ratelimit.RateLimitAction;
import com.ssolab.auth.security.ratelimit.SecurityRateLimitService;
import com.ssolab.auth.security.ratelimit.SecurityRequestIdentifiers;
import com.ssolab.auth.security.turnstile.TurnstileService;
import com.ssolab.auth.security.enumeration.AuthenticationTimingGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PasswordlessController {

    private final PasswordlessEmailOtpService emailOtpService;
    private final TotpService totpService;
    private final TotpRecoveryService recoveryService;
    private final RecoveryCodeService recoveryCodeService;
    private final PasswordlessSessionAuthenticationService sessionAuthenticationService;
    private final AuthSessionService authSessionService;
    private final OidcContinuationService continuationService;
    private final LogoutCoordinator logoutCoordinator;
    private final SecurityRateLimitService rateLimits;
    private final SecurityRequestIdentifiers identifiers;
    private final TurnstileService turnstile;
    private final AuthenticationTimingGuard timingGuard;

    public PasswordlessController(
        PasswordlessEmailOtpService emailOtpService,
        TotpService totpService,
        TotpRecoveryService recoveryService,
        RecoveryCodeService recoveryCodeService,
        PasswordlessSessionAuthenticationService sessionAuthenticationService,
        AuthSessionService authSessionService,
        OidcContinuationService continuationService,
        LogoutCoordinator logoutCoordinator,
        SecurityRateLimitService rateLimits,
        SecurityRequestIdentifiers identifiers,
        TurnstileService turnstile,
        AuthenticationTimingGuard timingGuard
    ) {
        this.emailOtpService = emailOtpService;
        this.totpService = totpService;
        this.recoveryService = recoveryService;
        this.recoveryCodeService = recoveryCodeService;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.authSessionService = authSessionService;
        this.continuationService = continuationService;
        this.logoutCoordinator = logoutCoordinator;
        this.rateLimits = rateLimits;
        this.identifiers = identifiers;
        this.turnstile = turnstile;
        this.timingGuard = timingGuard;
    }

    @PostMapping("/signup/start")
    OtpRequestResponse startSignup(
        @Valid @RequestBody SignupStartRequest request,
        HttpServletRequest servletRequest
    ) {
        String remoteAddress = identifiers.remoteAddress(servletRequest);
        rateLimits.check(RateLimitAction.SIGNUP_START, remoteAddress,
            identifiers.signup(request.userId(), request.email()), null);
        turnstile.verifyRequired(request.turnstileToken(), remoteAddress);
        return timingGuard.protect(() ->
            OtpRequestResponse.from(emailOtpService.startSignup(request.toCommand())));
    }

    @PostMapping("/signup/resend")
    ResponseEntity<?> resendSignup(
        @Valid @RequestBody ChallengeRequest request,
        HttpServletRequest servletRequest
    ) {
        String remoteAddress = identifiers.remoteAddress(servletRequest);
        rateLimits.check(RateLimitAction.EMAIL_OTP_RESEND, remoteAddress, null,
            request.challengeId().toString());
        turnstile.verifyRequired(request.turnstileToken(), remoteAddress);
        return otpRequest(emailOtpService.resendSignup(request.challengeId()));
    }

    @PostMapping("/signup/verify")
    ResponseEntity<?> verifySignup(
        @Valid @RequestBody ChallengeCodeRequest request,
        HttpServletRequest servletRequest
    ) {
        rateLimits.check(RateLimitAction.EMAIL_OTP_VERIFY,
            identifiers.remoteAddress(servletRequest), null, request.challengeId().toString());
        char[] code = request.copyCode();
        try {
            SignupVerificationResult result = emailOtpService.verifySignup(
                request.challengeId(), code
            );
            if (result.status() != OtpVerificationStatus.SUCCESS) {
                return authenticationFailed();
            }
            return ResponseEntity.ok(Map.of("registered", true));
        } finally {
            Arrays.fill(code, '\0');
        }
    }

    @PostMapping("/login/start")
    Map<String, Object> startLogin(
        @Valid @RequestBody(required = false) TurnstileRequest request,
        HttpServletRequest servletRequest
    ) {
        String remoteAddress = identifiers.remoteAddress(servletRequest);
        rateLimits.check(RateLimitAction.LOGIN_START, remoteAddress, null, null);
        turnstile.verifyRequired(request == null ? null : request.turnstileToken(), remoteAddress);
        return Map.of(
            "methods", List.of("EMAIL_OTP", "TOTP"),
            "message", "인증 방법을 선택해주세요."
        );
    }

    @PostMapping("/login/email/send")
    OtpRequestResponse sendLoginEmail(
        @Valid @RequestBody LoginOtpRequest request,
        HttpServletRequest servletRequest
    ) {
        String remoteAddress = identifiers.remoteAddress(servletRequest);
        rateLimits.check(RateLimitAction.EMAIL_OTP_SEND, remoteAddress,
            identifiers.userId(request.userId()), null);
        turnstile.verifyRequired(request.turnstileToken(), remoteAddress);
        return timingGuard.protect(() ->
            OtpRequestResponse.from(emailOtpService.sendLoginOtp(request.userId())));
    }

    @PostMapping("/login/email/resend")
    ResponseEntity<?> resendLoginEmail(
        @Valid @RequestBody ChallengeRequest request,
        HttpServletRequest servletRequest
    ) {
        String remoteAddress = identifiers.remoteAddress(servletRequest);
        rateLimits.check(RateLimitAction.EMAIL_OTP_RESEND, remoteAddress, null,
            request.challengeId().toString());
        turnstile.verifyRequired(request.turnstileToken(), remoteAddress);
        return otpRequest(emailOtpService.resendLogin(request.challengeId()));
    }

    @PostMapping("/login/email/verify")
    ResponseEntity<?> verifyLoginEmail(
        @Valid @RequestBody ChallengeCodeRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        rateLimits.check(RateLimitAction.EMAIL_OTP_VERIFY,
            identifiers.remoteAddress(servletRequest), null, request.challengeId().toString());
        char[] code = request.copyCode();
        try {
            LoginVerificationResult result = timingGuard.protect(() ->
                emailOtpService.verifyLogin(request.challengeId(), code));
            if (result.status() != OtpVerificationStatus.SUCCESS) {
                return authenticationFailed();
            }
            String continuation = continuationService.consume(servletRequest, servletResponse);
            sessionAuthenticationService.establish(
                servletRequest,
                result.userId(),
                AuthenticationMethod.EMAIL_OTP,
                SessionScope.NORMAL
            );
            return ResponseEntity.ok(new AuthenticationResponse(
                true, "email_otp", continuation
            ));
        } finally {
            Arrays.fill(code, '\0');
        }
    }

    @PostMapping("/login/totp/verify")
    ResponseEntity<?> verifyLoginTotp(
        @Valid @RequestBody LoginTotpRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        rateLimits.check(RateLimitAction.TOTP_VERIFY,
            identifiers.remoteAddress(servletRequest), identifiers.userId(request.userId()), null);
        char[] code = request.copyCode();
        try {
            TotpVerificationResult result = timingGuard.protect(() ->
                totpService.verifyLogin(request.userId(), code));
            if (result.status() != TotpVerificationStatus.SUCCESS) {
                return authenticationFailed();
            }
            String continuation = continuationService.consume(servletRequest, servletResponse);
            sessionAuthenticationService.establish(
                servletRequest,
                result.userId(),
                AuthenticationMethod.TOTP,
                SessionScope.NORMAL
            );
            return ResponseEntity.ok(new AuthenticationResponse(true, "totp", continuation));
        } finally {
            Arrays.fill(code, '\0');
        }
    }

    @PostMapping("/me/totp/enroll/start")
    ResponseEntity<?> startTotpEnrollment(
        @AuthenticationPrincipal PasswordlessPrincipal principal,
        HttpServletRequest request
    ) {
        if (!hasFreshReauthentication(principal, request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ApiError("REAUTHENTICATION_REQUIRED", "재인증이 필요합니다.")
            );
        }
        TotpEnrollmentStart start = totpService.startEnrollment(principal.userId());
        return ResponseEntity.ok(new TotpEnrollmentResponse(start.otpauthUri()));
    }

    @PostMapping("/me/totp/enroll/confirm")
    ResponseEntity<?> confirmTotpEnrollment(
        @AuthenticationPrincipal PasswordlessPrincipal principal,
        @Valid @RequestBody SensitiveCodeRequest request,
        HttpServletRequest servletRequest
    ) {
        rateLimits.check(RateLimitAction.TOTP_VERIFY,
            identifiers.remoteAddress(servletRequest), principal.userId().toString(), null);
        if (!hasFreshReauthentication(principal, servletRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ApiError("REAUTHENTICATION_REQUIRED", "재인증이 필요합니다.")
            );
        }
        char[] code = request.copyCode();
        try {
            TotpEnrollmentResult result = totpService.confirmEnrollment(principal.userId(), code);
            if (result.status() != TotpVerificationStatus.SUCCESS) {
                return authenticationFailed();
            }
            return ResponseEntity.ok(RecoveryCodesResponse.from(result.recoveryCodes()));
        } finally {
            Arrays.fill(code, '\0');
        }
    }

    @DeleteMapping("/me/totp")
    ResponseEntity<?> disableTotp(
        @AuthenticationPrincipal PasswordlessPrincipal principal,
        HttpServletRequest request
    ) {
        if (!hasFreshReauthentication(principal, request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ApiError("REAUTHENTICATION_REQUIRED", "재인증이 필요합니다.")
            );
        }
        totpService.disable(principal.userId(), UUID.randomUUID().toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/recovery-codes/regenerate")
    ResponseEntity<?> regenerateRecoveryCodes(
        @AuthenticationPrincipal PasswordlessPrincipal principal,
        HttpServletRequest request
    ) {
        if (!hasFreshReauthentication(principal, request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ApiError("REAUTHENTICATION_REQUIRED", "재인증이 필요합니다.")
            );
        }
        RecoveryCodeBatch batch = recoveryCodeService.regenerate(principal.userId());
        return ResponseEntity.ok(RecoveryCodesResponse.from(batch));
    }

    @PostMapping("/totp/recovery/start")
    ResponseEntity<?> startRecovery(
        @Valid @RequestBody RecoveryStartRequest request,
        HttpServletRequest servletRequest
    ) {
        String remoteAddress = identifiers.remoteAddress(servletRequest);
        rateLimits.check(RateLimitAction.RECOVERY_CODE_VERIFY, remoteAddress,
            identifiers.userId(request.userId()), null);
        turnstile.verifyRequired(request.turnstileToken(), remoteAddress);
        TotpRecoveryResult result = timingGuard.protect(() -> recoveryService.start(
            request.userId(), request.recoveryCode()
        ));
        if (!result.success()) {
            return authenticationFailed();
        }
        sessionAuthenticationService.establish(
            servletRequest,
            result.userId(),
            AuthenticationMethod.RECOVERY_CODE,
            SessionScope.RECOVERY_ONLY
        );
        return ResponseEntity.ok(new TotpEnrollmentResponse(result.enrollment().otpauthUri()));
    }

    @PostMapping("/totp/recovery/enroll/confirm")
    ResponseEntity<?> confirmRecoveryEnrollment(
        @AuthenticationPrincipal PasswordlessPrincipal principal,
        @Valid @RequestBody SensitiveCodeRequest request,
        HttpServletRequest servletRequest
    ) {
        rateLimits.check(RateLimitAction.TOTP_VERIFY,
            identifiers.remoteAddress(servletRequest), principal.userId().toString(), null);
        char[] code = request.copyCode();
        try {
            TotpEnrollmentResult result = totpService.confirmEnrollment(principal.userId(), code);
            if (result.status() != TotpVerificationStatus.SUCCESS) {
                return authenticationFailed();
            }
            RecoveryCodesResponse response = RecoveryCodesResponse.from(result.recoveryCodes());
            sessionAuthenticationService.invalidateCurrent(servletRequest);
            return ResponseEntity.ok(response);
        } finally {
            Arrays.fill(code, '\0');
        }
    }

    @DeleteMapping("/me/session")
    ResponseEntity<Void> invalidateCurrentSession(
        @AuthenticationPrincipal PasswordlessPrincipal principal,
        HttpServletRequest request
    ) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            logoutCoordinator.logoutOne(principal.userId(), session.getId(), session.getId());
        }
        sessionAuthenticationService.invalidateCurrent(request);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<?> otpRequest(OtpRequestResult result) {
        if (result.status() != OtpVerificationStatus.SUCCESS) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                new ApiError("OTP_REQUEST_REJECTED", "잠시 후 다시 시도해주세요.")
            );
        }
        return ResponseEntity.ok(OtpRequestResponse.from(result));
    }

    private boolean hasFreshReauthentication(
        PasswordlessPrincipal principal,
        HttpServletRequest request
    ) {
        HttpSession session = request.getSession(false);
        return session != null && authSessionService.hasFreshReauthentication(
            session.getId(), principal.userId()
        );
    }

    private ResponseEntity<ApiError> authenticationFailed() {
        return ResponseEntity.badRequest().body(
            new ApiError("AUTHENTICATION_FAILED", "인증에 실패했습니다.")
        );
    }
}
