package com.ssolab.auth.passwordless.totp;

public record TotpEnrollmentStart(String otpauthUri) {

    @Override
    public String toString() {
        return "TotpEnrollmentStart[otpauthUri=REDACTED]";
    }
}
