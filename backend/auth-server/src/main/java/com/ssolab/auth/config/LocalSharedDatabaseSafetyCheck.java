package com.ssolab.auth.config;

import com.ssolab.auth.oidc.config.OidcProperties;
import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-shared-db")
public class LocalSharedDatabaseSafetyCheck implements ApplicationRunner, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalSharedDatabaseSafetyCheck.class);
    private static final Set<String> PRODUCTION_CLIENT_IDS = Set.of(
        "hr-client", "approval-client", "admin-client"
    );

    private final Environment environment;
    private final OidcProperties oidc;

    public LocalSharedDatabaseSafetyCheck(Environment environment, OidcProperties oidc) {
        this.environment = environment;
        this.oidc = oidc;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
        LOGGER.warn(
            "LOCAL SHARED DATABASE MODE is active: Flyway and Bootstrap Admin are disabled; "
                + "sessions and local-only OIDC data are written to the shared auth schema"
        );
    }

    void validate() {
        requireTrue("sso.local-shared-database.acknowledged");
        requireFalse("spring.flyway.enabled");
        requireFalse("sso.bootstrap-admin.enabled");
        requireFalse("sso.security.turnstile.enabled");
        requireFalse("sso.test-support.enabled");
        validateTunnelDatasource(environment.getProperty("spring.datasource.url"));
        validateLocalOidc();
    }

    private void validateLocalOidc() {
        URI expectedIssuer = URI.create("http://127.0.0.1:5173");
        if (!expectedIssuer.equals(oidc.getIssuer())) {
            throw new IllegalStateException("local shared DB mode requires the local Auth issuer");
        }
        Set<String> clientIds = new HashSet<>();
        oidc.getClients().forEach((name, client) -> {
            String clientId = client.getClientId();
            if (clientId == null || !clientId.startsWith("sso-local-")
                || PRODUCTION_CLIENT_IDS.contains(clientId)) {
                throw new IllegalStateException(
                    "local shared DB mode requires a distinct local-only " + name + " client ID"
                );
            }
            if (!clientIds.add(clientId)) {
                throw new IllegalStateException("local shared DB OIDC client IDs must be unique");
            }
            requireLoopbackUri(name + " redirect URI", client.getRedirectUri());
            requireLoopbackUri(name + " post-logout redirect URI", client.getPostLogoutRedirectUri());
        });
    }

    private static void requireLoopbackUri(String label, URI uri) {
        String host = uri == null ? null : uri.getHost();
        if (!"http".equalsIgnoreCase(uri == null ? null : uri.getScheme())
            || host == null || !"127.0.0.1".equals(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("local shared DB mode requires a loopback " + label);
        }
    }

    private static void validateTunnelDatasource(String value) {
        if (value == null || !value.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("local shared DB mode requires a PostgreSQL JDBC URL");
        }
        URI uri = URI.create(value.substring("jdbc:".length()));
        String host = uri.getHost();
        if (!("127.0.0.1".equals(host) || "host.docker.internal".equalsIgnoreCase(host))) {
            throw new IllegalStateException(
                "local shared DB mode only permits a loopback SSH tunnel endpoint"
            );
        }
    }

    private void requireTrue(String key) {
        if (!environment.getProperty(key, Boolean.class, false)) {
            throw new IllegalStateException(key + " must be true in local shared DB mode");
        }
    }

    private void requireFalse(String key) {
        if (environment.getProperty(key, Boolean.class, true)) {
            throw new IllegalStateException(key + " must be false in local shared DB mode");
        }
    }
}
