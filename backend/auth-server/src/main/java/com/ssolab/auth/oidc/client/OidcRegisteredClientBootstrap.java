package com.ssolab.auth.oidc.client;

import com.ssolab.auth.oidc.config.OidcProperties;
import com.ssolab.auth.secret.SecretProviderRegistry;
import com.ssolab.auth.secret.SecretValue;
import java.nio.CharBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

@Component
public class OidcRegisteredClientBootstrap implements ApplicationRunner {

    private final ManagedRegisteredClientRepository repository;
    private final SecretProviderRegistry secrets;
    private final PasswordEncoder passwordEncoder;
    private final OidcProperties properties;

    public OidcRegisteredClientBootstrap(
        ManagedRegisteredClientRepository repository,
        SecretProviderRegistry secrets,
        PasswordEncoder passwordEncoder,
        OidcProperties properties
    ) {
        this.repository = repository;
        this.secrets = secrets;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        properties.validate();
        properties.getClients().values().forEach(this::upsert);
    }

    private void upsert(OidcProperties.Client configured) {
        RegisteredClient existing = repository.findByClientId(configured.getClientId());
        char[] rawSecret;
        try (SecretValue value = secrets.getSecret(configured.secret())) {
            rawSecret = value.copy();
        }
        try {
            String encodedSecret = existing != null
                && passwordEncoder.matches(CharBuffer.wrap(rawSecret), existing.getClientSecret())
                ? existing.getClientSecret()
                : passwordEncoder.encode(CharBuffer.wrap(rawSecret));
            RegisteredClient.Builder builder = existing == null
                ? RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientIdIssuedAt(Instant.now())
                : RegisteredClient.from(existing);
            RegisteredClient desired = builder
                .clientId(configured.getClientId())
                .clientSecret(encodedSecret)
                .clientName(configured.getClientName())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> {
                    uris.clear();
                    uris.add(configured.getRedirectUri().toString());
                })
                .postLogoutRedirectUris(uris -> {
                    uris.clear();
                    uris.add(configured.getPostLogoutRedirectUri().toString());
                })
                .scopes(scopes -> {
                    scopes.clear();
                    scopes.addAll(configured.getScopes());
                })
                .clientSettings(ClientSettings.builder()
                    .requireProofKey(true)
                    .requireAuthorizationConsent(false)
                    .setting("sso.back-channel-logout-uri",
                        configured.getBackChannelLogoutUri().toString())
                    .build())
                .tokenSettings(TokenSettings.builder()
                    .authorizationCodeTimeToLive(properties.getAuthorizationCodeTtl())
                    .accessTokenTimeToLive(properties.getAccessTokenTtl())
                    .refreshTokenTimeToLive(properties.getRefreshTokenTtl())
                    .reuseRefreshTokens(false)
                    .build())
                .build();
            if (existing == null) {
                repository.save(desired);
            } else {
                repository.update(desired);
            }
        } finally {
            Arrays.fill(rawSecret, '\0');
        }
    }
}
