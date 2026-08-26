package com.ssolab.admin.internal;

import com.ssolab.shared.secret.SecretFileValue;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sso.internal-admin")
public record InternalAdminProperties(URI baseUri, String clientId, String serviceSecret,
    String serviceSecretFile) {
    public InternalAdminProperties {
        if (baseUri == null || !baseUri.isAbsolute() || baseUri.getFragment() != null
            || baseUri.getQuery() != null || baseUri.toString().contains("*")) {
            throw new IllegalArgumentException("internal admin base URI must be exact and absolute");
        }
        if (!baseUri.getPath().endsWith("/internal/admin/v1")) {
            throw new IllegalArgumentException("internal admin base URI has an invalid path");
        }
        serviceSecret = SecretFileValue.resolve(
            serviceSecret, serviceSecretFile, "Admin internal service secret"
        );
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("internal admin service credential is required");
        }
    }
}
