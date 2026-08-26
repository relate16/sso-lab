package com.ssolab.auth.passwordless.api;

import com.ssolab.auth.identity.service.IdentityConflictException;
import com.ssolab.auth.passwordless.mail.MailDeliveryUnavailableException;
import com.ssolab.auth.security.ratelimit.RateLimitExceededException;
import com.ssolab.auth.security.turnstile.TurnstileVerificationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PasswordlessApiExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> validationFailure(Exception exception) {
        return ResponseEntity.badRequest().body(
            new ApiError("INVALID_REQUEST", "요청을 처리할 수 없습니다.")
        );
    }

    @ExceptionHandler(IdentityConflictException.class)
    ResponseEntity<ApiError> conflict(IdentityConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            new ApiError("IDENTITY_CONFLICT", "요청한 정보를 사용할 수 없습니다.")
        );
    }

    @ExceptionHandler(MailDeliveryUnavailableException.class)
    ResponseEntity<ApiError> mailUnavailable(MailDeliveryUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            new ApiError("MAIL_UNAVAILABLE", "인증 메일을 전송할 수 없습니다.")
        );
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiError> rateLimited(RateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header(HttpHeaders.RETRY_AFTER,
                Long.toString(Math.max(1, exception.retryAfter().toSeconds())))
            .body(new ApiError("REQUEST_THROTTLED", "잠시 후 다시 시도해주세요."));
    }

    @ExceptionHandler(TurnstileVerificationException.class)
    ResponseEntity<ApiError> humanVerificationFailed(TurnstileVerificationException exception) {
        return ResponseEntity.badRequest()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(new ApiError("HUMAN_VERIFICATION_FAILED", "요청을 확인할 수 없습니다."));
    }
}
