package com.ssolab.auth.admin.internal;

import com.ssolab.auth.secret.SecretProviderType;
import com.ssolab.auth.secret.SecretReference;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sso.internal-admin")
public record InternalAdminProperties(
    String allowedClientId,
    SecretProviderType secretProvider,
    String secretReference
) {
    public InternalAdminProperties {
        if (allowedClientId == null || allowedClientId.isBlank()) {
            throw new IllegalArgumentException("internal admin client id is required");
        }
        if (secretProvider == null || secretReference == null || secretReference.isBlank()) {
            throw new IllegalArgumentException("internal admin service credential is required");
        }
    }

    public SecretReference serviceSecret() {
        return new SecretReference(secretProvider, secretReference);
    }
}
