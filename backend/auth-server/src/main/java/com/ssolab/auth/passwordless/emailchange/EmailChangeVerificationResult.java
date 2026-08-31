package com.ssolab.auth.passwordless.emailchange;

import com.ssolab.auth.passwordless.otp.OtpVerificationStatus;
import java.util.UUID;

public record EmailChangeVerificationResult(
    OtpVerificationStatus status,
    UUID userId
) {
    public static EmailChangeVerificationResult failed(OtpVerificationStatus status) {
        return new EmailChangeVerificationResult(status, null);
    }
}
