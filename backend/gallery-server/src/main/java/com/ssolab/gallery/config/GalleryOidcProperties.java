package com.ssolab.gallery.config;

import com.ssolab.shared.secret.SecretFileValue;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sso.oidc-client")
public record GalleryOidcProperties(String registrationId, String clientId, String clientSecret,
    String clientSecretFile, URI issuer, URI authorizationUri, URI tokenUri, URI jwkSetUri,
    URI userInfoUri, String redirectUri, URI postLogoutRedirectUri) {
    public GalleryOidcProperties {
        requireText("registrationId", registrationId); requireText("clientId", clientId);
        clientSecret = SecretFileValue.resolve(clientSecret, clientSecretFile,
            "Gallery OIDC client secret");
        requireUri("issuer", issuer); requireUri("authorizationUri", authorizationUri);
        requireUri("tokenUri", tokenUri); requireUri("jwkSetUri", jwkSetUri);
        requireUri("userInfoUri", userInfoUri); requireUri("postLogoutRedirectUri", postLogoutRedirectUri);
        requireText("redirectUri", redirectUri);
        if (redirectUri.contains("*")) throw new IllegalArgumentException("redirectUri must be exact");
    }
    private static void requireText(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be configured");
    }
    private static void requireUri(String name, URI value) {
        if (value == null || !value.isAbsolute() || value.getFragment() != null
            || value.toString().contains("*")) throw new IllegalArgumentException(name + " must be exact");
    }
}
