package com.ssolab.auth.passwordless.session;

public enum SessionValidationStatus {
    VALID,
    NOT_FOUND,
    INVALIDATED,
    IDLE_EXPIRED,
    ABSOLUTE_EXPIRED,
    ACCOUNT_SUSPENDED,
    WRONG_SCOPE
}
