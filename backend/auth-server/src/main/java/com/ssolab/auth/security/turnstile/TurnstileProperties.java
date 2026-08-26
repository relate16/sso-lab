package com.ssolab.auth.security.turnstile;

import com.ssolab.auth.secret.SecretProviderType;
import com.ssolab.auth.secret.SecretReference;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sso.security.turnstile")
public record TurnstileProperties(
    boolean enabled,
    URI verificationUri,
    SecretProviderType secretProvider,
    String secretReference
) {
    public TurnstileProperties {
        if (verificationUri == null || !verificationUri.isAbsolute()
            || verificationUri.getFragment() != null) {
            throw new IllegalArgumentException("Turnstile verification URI must be absolute");
        }
        if (secretProvider == null) {
            throw new IllegalArgumentException("Turnstile SecretProvider must be configured");
        }
        if (secretReference == null || secretReference.isBlank()) {
            throw new IllegalArgumentException("Turnstile secret reference must be configured");
        }
    }

    public SecretReference secret() {
        return new SecretReference(secretProvider, secretReference);
    }
}
