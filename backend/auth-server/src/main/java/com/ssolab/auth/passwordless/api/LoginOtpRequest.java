package com.ssolab.auth.passwordless.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginOtpRequest(
    @NotBlank @Size(max = 30) String userId,
    @Size(max = 2048) String turnstileToken
) {

    public LoginOtpRequest(String userId) {
        this(userId, null);
    }

    @Override
    public String toString() {
        return "LoginOtpRequest[userId=REDACTED, turnstileToken=REDACTED]";
    }
}
