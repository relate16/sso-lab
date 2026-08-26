package com.ssolab.auth.identity.crypto;

public class IdentityCryptoException extends RuntimeException {

    public IdentityCryptoException(String message) {
        super(message);
    }

    public IdentityCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
