package com.ssolab.admin.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice(assignableTypes = AdminApiController.class)
public class AdminApiExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> reauthRequired() {
        return response(HttpStatus.FORBIDDEN, "reauthentication_required");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid() {
        return response(HttpStatus.BAD_REQUEST, "invalid_request");
    }

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<Map<String, String>> upstream(RestClientResponseException exception) {
        int rawStatus = exception.getStatusCode().value();
        HttpStatus status = HttpStatus.resolve(rawStatus);
        if (status == null || status.is5xxServerError()) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return response(status, "admin_operation_failed");
    }

    private ResponseEntity<Map<String, String>> response(HttpStatus status, String code) {
        return ResponseEntity.status(status)
            .header("Cache-Control", "no-store")
            .body(Map.of("error", code));
    }
}
