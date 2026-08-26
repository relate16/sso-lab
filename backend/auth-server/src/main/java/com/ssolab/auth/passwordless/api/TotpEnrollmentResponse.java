package com.ssolab.auth.passwordless.api;

public record TotpEnrollmentResponse(String otpauthUri) {

    @Override
    public String toString() {
        return "TotpEnrollmentResponse[otpauthUri=REDACTED]";
    }
}
