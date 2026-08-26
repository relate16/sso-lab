package com.ssolab.auth.secret;

public class SecretUnavailableException extends RuntimeException {

    public SecretUnavailableException(String message) {
        super(message);
    }

    public SecretUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
