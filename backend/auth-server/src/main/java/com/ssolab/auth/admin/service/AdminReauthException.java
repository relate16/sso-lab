package com.ssolab.auth.admin.service;

public class AdminReauthException extends RuntimeException {
    private final String code;

    public AdminReauthException(String code) {
        super("administrator re-authentication failed");
        this.code = code;
    }

    public String code() {
        return code;
    }
}
