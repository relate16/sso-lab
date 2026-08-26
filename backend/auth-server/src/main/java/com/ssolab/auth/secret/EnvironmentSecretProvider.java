package com.ssolab.auth.secret;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentSecretProvider implements SecretProvider {

    private final Environment environment;

    public EnvironmentSecretProvider(Environment environment) {
        this.environment = environment;
    }

    @Override
    public SecretProviderType type() {
        return SecretProviderType.ENVIRONMENT;
    }

    @Override
    public SecretValue getSecret(String reference) {
        String value = environment.getProperty(reference);
        if (value == null || value.isBlank()) {
            throw new SecretUnavailableException("secret is unavailable for environment reference: " + reference);
        }
        return SecretValue.of(value);
    }
}
