package com.ssolab.auth.passwordless.otp;

import java.util.UUID;

public record LoginVerificationResult(OtpVerificationStatus status, UUID userId) {

    public static LoginVerificationResult failed(OtpVerificationStatus status) {
        return new LoginVerificationResult(status, null);
    }
}
