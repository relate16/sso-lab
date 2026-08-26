package com.ssolab.auth.identity.service;

public class IdentityConflictException extends RuntimeException {

    public IdentityConflictException(String message) {
        super(message);
    }

    public IdentityConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
