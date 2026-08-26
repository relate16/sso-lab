package com.ssolab.auth.passwordless.totp;

import com.ssolab.auth.passwordless.recovery.RecoveryCodeBatch;

public record TotpEnrollmentResult(
    TotpVerificationStatus status,
    RecoveryCodeBatch recoveryCodes
) {

    public static TotpEnrollmentResult failed(TotpVerificationStatus status) {
        return new TotpEnrollmentResult(status, null);
    }
}
