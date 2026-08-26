package com.ssolab.auth.passwordless.totp;

public enum TotpVerificationStatus {
    SUCCESS,
    INVALID,
    NOT_ENROLLED,
    PENDING_ENROLLMENT_REQUIRED,
    REPLAYED,
    ACCOUNT_UNAVAILABLE
}
