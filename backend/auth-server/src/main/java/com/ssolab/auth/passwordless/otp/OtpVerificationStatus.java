package com.ssolab.auth.passwordless.otp;

public enum OtpVerificationStatus {
    SUCCESS,
    INVALID,
    EXPIRED,
    ATTEMPTS_EXCEEDED,
    ALREADY_USED,
    RESEND_TOO_SOON,
    NOT_FOUND,
    ACCOUNT_UNAVAILABLE,
    IDENTITY_CONFLICT
}
