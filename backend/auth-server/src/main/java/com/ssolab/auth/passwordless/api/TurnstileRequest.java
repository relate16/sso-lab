package com.ssolab.auth.passwordless.api;

import jakarta.validation.constraints.Size;

public record TurnstileRequest(@Size(max = 2048) String turnstileToken) {
    @Override
    public String toString() {
        return "TurnstileRequest[turnstileToken=REDACTED]";
    }
}
