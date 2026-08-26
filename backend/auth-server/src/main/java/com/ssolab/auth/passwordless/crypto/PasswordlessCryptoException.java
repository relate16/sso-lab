package com.ssolab.auth.passwordless.crypto;

public class PasswordlessCryptoException extends RuntimeException {

    public PasswordlessCryptoException(String message) {
        super(message);
    }

    public PasswordlessCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
