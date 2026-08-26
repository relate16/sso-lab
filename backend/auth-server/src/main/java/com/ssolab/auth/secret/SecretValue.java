package com.ssolab.auth.secret;

import java.util.Arrays;

public final class SecretValue implements AutoCloseable {

    private char[] value;

    private SecretValue(char[] value) {
        this.value = value.clone();
    }

    public static SecretValue of(String value) {
        if (value == null || value.isBlank()) {
            throw new SecretUnavailableException("secret value is missing or blank");
        }
        return new SecretValue(value.toCharArray());
    }

    public char[] copy() {
        if (value == null) {
            throw new IllegalStateException("secret value has already been destroyed");
        }
        return value.clone();
    }

    @Override
    public void close() {
        if (value != null) {
            Arrays.fill(value, '\0');
            value = null;
        }
    }

    @Override
    public String toString() {
        return "SecretValue[REDACTED]";
    }
}
