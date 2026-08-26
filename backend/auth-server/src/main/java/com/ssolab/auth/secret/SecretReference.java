package com.ssolab.auth.secret;

import java.util.Objects;

public record SecretReference(SecretProviderType provider, String reference) {

    public SecretReference {
        Objects.requireNonNull(provider, "provider must not be null");
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("secret reference must not be blank");
        }
        reference = reference.trim();
    }
}
