package com.ssolab.auth.passwordless.crypto;

import java.util.Arrays;

public final class SensitiveCode implements AutoCloseable {

    private char[] value;

    private SensitiveCode(char[] value) {
        this.value = value.clone();
    }

    public static SensitiveCode of(char[] value) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException("sensitive code must not be empty");
        }
        return new SensitiveCode(value);
    }

    public char[] copy() {
        if (value == null) {
            throw new IllegalStateException("sensitive code has already been destroyed");
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
        return "SensitiveCode[REDACTED]";
    }
}
