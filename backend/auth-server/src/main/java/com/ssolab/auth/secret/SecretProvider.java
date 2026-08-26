package com.ssolab.auth.secret;

public interface SecretProvider {

    SecretProviderType type();

    SecretValue getSecret(String reference);
}
