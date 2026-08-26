package com.ssolab.admin.config;

import com.ssolab.shared.secret.SecretFileValue;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sso.oidc-client")
public record OidcBffProperties(String registrationId, String clientId, String clientSecret,
    String clientSecretFile,
    URI issuer, URI authorizationUri, URI tokenUri, URI jwkSetUri, URI userInfoUri,
    String redirectUri, URI postLogoutRedirectUri) {
    public OidcBffProperties {
        requireText("registrationId", registrationId); requireText("clientId", clientId);
        clientSecret = SecretFileValue.resolve(
            clientSecret, clientSecretFile, "Admin OIDC client secret"
        );
        requireExact("issuer", issuer);
        requireExact("authorizationUri", authorizationUri); requireExact("tokenUri", tokenUri);
        requireExact("jwkSetUri", jwkSetUri); requireExact("userInfoUri", userInfoUri);
        requireText("redirectUri", redirectUri);
        requireExact("postLogoutRedirectUri", postLogoutRedirectUri);
        if (redirectUri.contains("*")) throw new IllegalArgumentException("redirectUri must not contain a wildcard");
    }
    private static void requireText(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be configured");
    }
    private static void requireExact(String name, URI value) {
        if (value == null || !value.isAbsolute() || value.getFragment() != null
            || value.toString().contains("*")) throw new IllegalArgumentException(name + " must be an exact absolute URI");
    }
}
