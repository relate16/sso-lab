package com.ssolab.auth.passwordless.totp;

import java.util.UUID;

public record TotpVerificationResult(TotpVerificationStatus status, UUID userId) {

    public static TotpVerificationResult failed(TotpVerificationStatus status) {
        return new TotpVerificationResult(status, null);
    }
}
