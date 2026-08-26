package com.ssolab.auth.passwordless.otp;

import java.util.UUID;

public record SignupVerificationResult(OtpVerificationStatus status, UUID userId) {

    public static SignupVerificationResult failed(OtpVerificationStatus status) {
        return new SignupVerificationResult(status, null);
    }
}
