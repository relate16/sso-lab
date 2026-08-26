package com.ssolab.auth.oidc.config;

import com.ssolab.auth.secret.SecretProviderType;
import com.ssolab.auth.secret.SecretReference;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sso.oidc")
public class OidcProperties {

    private URI issuer = URI.create("http://localhost:8080");
    private String loginPageUri = "/?login=required";
    private Duration authorizationCodeTtl = Duration.ofMinutes(2);
    private Duration accessTokenTtl = Duration.ofMinutes(5);
    private Duration idTokenTtl = Duration.ofMinutes(5);
    private Duration refreshTokenTtl = Duration.ofHours(8);
    private Duration logoutTokenTtl = Duration.ofMinutes(2);
    private Signing signing = new Signing();
    private Map<String, Client> clients = new LinkedHashMap<>();

    public URI getIssuer() {
        return issuer;
    }

    public void setIssuer(URI issuer) {
        this.issuer = issuer;
    }

    public String getLoginPageUri() {
        return loginPageUri;
    }

    public void setLoginPageUri(String loginPageUri) {
        this.loginPageUri = loginPageUri;
    }

    public Duration getAuthorizationCodeTtl() {
        return authorizationCodeTtl;
    }

    public void setAuthorizationCodeTtl(Duration authorizationCodeTtl) {
        this.authorizationCodeTtl = authorizationCodeTtl;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getIdTokenTtl() {
        return idTokenTtl;
    }

    public void setIdTokenTtl(Duration idTokenTtl) {
        this.idTokenTtl = idTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public Duration getLogoutTokenTtl() {
        return logoutTokenTtl;
    }

    public void setLogoutTokenTtl(Duration logoutTokenTtl) {
        this.logoutTokenTtl = logoutTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public Signing getSigning() {
        return signing;
    }

    public void setSigning(Signing signing) {
        this.signing = signing;
    }

    public Map<String, Client> getClients() {
        return clients;
    }

    public void setClients(Map<String, Client> clients) {
        this.clients = clients;
    }

    public void validate() {
        if (issuer == null || !issuer.isAbsolute() || issuer.getFragment() != null) {
            throw new IllegalStateException("OIDC issuer must be an absolute URI without a fragment");
        }
        if (loginPageUri == null || loginPageUri.isBlank()) {
            throw new IllegalStateException("OIDC login page URI must be configured");
        }
        if (logoutTokenTtl == null || logoutTokenTtl.isNegative() || logoutTokenTtl.isZero()) {
            throw new IllegalStateException("logout token TTL must be positive");
        }
        if (clients.size() != 3
            || !clients.keySet().containsAll(Set.of("hr", "approval", "admin"))) {
            throw new IllegalStateException("exactly hr, approval and admin OIDC clients are required");
        }
        clients.forEach((name, client) -> client.validate(name));
    }

    public static class Signing {
        private String keyId = "sso-lab-signing-v1";
        private SecretProviderType provider = SecretProviderType.ENVIRONMENT;
        private String privateKeyReference = "SSO_JWT_PRIVATE_KEY";
        private String publicKeyReference = "SSO_JWT_PUBLIC_KEY";

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public SecretProviderType getProvider() {
            return provider;
        }

        public void setProvider(SecretProviderType provider) {
            this.provider = provider;
        }

        public String getPrivateKeyReference() {
            return privateKeyReference;
        }

        public void setPrivateKeyReference(String privateKeyReference) {
            this.privateKeyReference = privateKeyReference;
        }

        public String getPublicKeyReference() {
            return publicKeyReference;
        }

        public void setPublicKeyReference(String publicKeyReference) {
            this.publicKeyReference = publicKeyReference;
        }

        public SecretReference privateKeySecret() {
            return new SecretReference(provider, privateKeyReference);
        }

        public SecretReference publicKeySecret() {
            return new SecretReference(provider, publicKeyReference);
        }
    }

    public static class Client {
        private String clientId;
        private String clientName;
        private SecretProviderType secretProvider = SecretProviderType.ENVIRONMENT;
        private String secretReference;
        private URI redirectUri;
        private URI postLogoutRedirectUri;
        private URI backChannelLogoutUri;
        private Set<String> scopes = Set.of("openid", "profile", "email");

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientName() {
            return clientName;
        }

        public void setClientName(String clientName) {
            this.clientName = clientName;
        }

        public SecretProviderType getSecretProvider() {
            return secretProvider;
        }

        public void setSecretProvider(SecretProviderType secretProvider) {
            this.secretProvider = secretProvider;
        }

        public String getSecretReference() {
            return secretReference;
        }

        public void setSecretReference(String secretReference) {
            this.secretReference = secretReference;
        }

        public URI getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(URI redirectUri) {
            this.redirectUri = redirectUri;
        }

        public URI getPostLogoutRedirectUri() {
            return postLogoutRedirectUri;
        }

        public void setPostLogoutRedirectUri(URI postLogoutRedirectUri) {
            this.postLogoutRedirectUri = postLogoutRedirectUri;
        }

        public URI getBackChannelLogoutUri() {
            return backChannelLogoutUri;
        }

        public void setBackChannelLogoutUri(URI backChannelLogoutUri) {
            this.backChannelLogoutUri = backChannelLogoutUri;
        }

        public Set<String> getScopes() {
            return scopes;
        }

        public void setScopes(Set<String> scopes) {
            this.scopes = scopes;
        }

        public SecretReference secret() {
            return new SecretReference(secretProvider, secretReference);
        }

        private void validate(String logicalName) {
            if (clientId == null || clientId.isBlank() || clientName == null || clientName.isBlank()) {
                throw new IllegalStateException(logicalName + " OIDC client identity is incomplete");
            }
            secret();
            validateExactUri(logicalName + " redirect URI", redirectUri);
            validateExactUri(logicalName + " post-logout redirect URI", postLogoutRedirectUri);
            validateExactUri(logicalName + " back-channel logout URI", backChannelLogoutUri);
            if (!scopes.contains("openid")) {
                throw new IllegalStateException(logicalName + " client must request openid scope");
            }
        }

        private static void validateExactUri(String label, URI uri) {
            if (uri == null || !uri.isAbsolute() || uri.getFragment() != null
                || uri.toString().contains("*")
                || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalStateException(label + " must be an exact absolute allow-list URI");
            }
        }
    }
}
