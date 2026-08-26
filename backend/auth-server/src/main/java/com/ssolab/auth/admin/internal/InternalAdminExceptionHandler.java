package com.ssolab.auth.admin.internal;

import com.ssolab.auth.admin.service.AdminReauthException;
import com.ssolab.auth.identity.service.IdentityConflictException;
import com.ssolab.auth.identity.service.IdentityNotFoundException;
import com.ssolab.auth.security.ratelimit.RateLimitExceededException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = InternalAdminController.class)
public class InternalAdminExceptionHandler {

    @ExceptionHandler(IdentityNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound() {
        return response(HttpStatus.NOT_FOUND, "resource_not_found");
    }

    @ExceptionHandler(IdentityConflictException.class)
    ResponseEntity<Map<String, String>> conflict() {
        return response(HttpStatus.CONFLICT, "identity_conflict");
    }

    @ExceptionHandler(AdminReauthException.class)
    ResponseEntity<Map<String, String>> reauth(AdminReauthException exception) {
        HttpStatus status = exception.code().contains("resend")
            ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.FORBIDDEN;
        return response(status, exception.code());
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        MethodArgumentNotValidException.class
    })
    ResponseEntity<Map<String, String>> invalid() {
        return response(HttpStatus.BAD_REQUEST, "invalid_request");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<Map<String, String>> rateLimited(RateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Cache-Control", "no-store")
            .header("Retry-After", Long.toString(
                Math.max(1, exception.retryAfter().toSeconds())))
            .body(Map.of("error", "request_throttled"));
    }

    private ResponseEntity<Map<String, String>> response(HttpStatus status, String code) {
        return ResponseEntity.status(status)
            .header("Cache-Control", "no-store")
            .body(Map.of("error", code));
    }
}
