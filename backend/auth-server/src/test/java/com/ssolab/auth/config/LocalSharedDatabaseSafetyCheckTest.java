package com.ssolab.auth.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ssolab.auth.oidc.config.OidcProperties;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

class LocalSharedDatabaseSafetyCheckTest {

    private final Environment environment = org.mockito.Mockito.mock(Environment.class);
    private OidcProperties oidc;
    private LocalSharedDatabaseSafetyCheck check;

    @BeforeEach
    void setUp() {
        oidc = localOidc();
        check = new LocalSharedDatabaseSafetyCheck(environment, oidc);
        when(environment.getProperty("sso.local-shared-database.acknowledged", Boolean.class, false))
            .thenReturn(true);
        for (String key : new String[]{
            "spring.flyway.enabled", "sso.bootstrap-admin.enabled",
            "sso.security.turnstile.enabled", "sso.test-support.enabled"
        }) {
            when(environment.getProperty(key, Boolean.class, true)).thenReturn(false);
        }
        when(environment.getProperty("spring.datasource.url"))
            .thenReturn("jdbc:postgresql://host.docker.internal:15432/sso_lab");
    }

    @Test
    void acceptsExplicitTunnelOnlyConfigurationBeforeOtherRunners() {
        assertThatCode(check::validate).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(check.getOrder())
            .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void rejectsEnabledFlyway() {
        when(environment.getProperty("spring.flyway.enabled", Boolean.class, true)).thenReturn(true);
        assertThatThrownBy(check::validate).hasMessageContaining("spring.flyway.enabled");
    }

    @Test
    void permitsExplicitRuntimeMailDeliveryWithoutWeakeningDatabaseGuards() {
        when(environment.getProperty("sso.mail.gmail.enabled", Boolean.class, true))
            .thenReturn(true);
        assertThatCode(check::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonTunnelDatasource() {
        when(environment.getProperty("spring.datasource.url"))
            .thenReturn("jdbc:postgresql://postgres.example.test:5432/sso_lab");
        assertThatThrownBy(check::validate).hasMessageContaining("loopback SSH tunnel");
    }

    @Test
    void rejectsProductionClientIdentity() {
        oidc.getClients().get("hr").setClientId("hr-client");
        assertThatThrownBy(check::validate).hasMessageContaining("local-only hr client ID");
    }

    @Test
    void rejectsProductionRedirectUri() {
        oidc.getClients().get("hr").setRedirectUri(
            URI.create("https://hr.example.test/login/oauth2/code/hr-client")
        );
        assertThatThrownBy(check::validate).hasMessageContaining("loopback hr redirect URI");
    }

    private static OidcProperties localOidc() {
        OidcProperties properties = new OidcProperties();
        properties.setIssuer(URI.create("http://127.0.0.1:5173"));
        Map<String, OidcProperties.Client> clients = new LinkedHashMap<>();
        clients.put("hr", client("sso-local-hr-client", 5175, "hr-client"));
        clients.put("approval", client("sso-local-approval-client", 5176, "approval-client"));
        clients.put("admin", client("sso-local-admin-client", 5174, "admin-client"));
        properties.setClients(clients);
        return properties;
    }

    private static OidcProperties.Client client(String clientId, int port, String registrationId) {
        OidcProperties.Client client = new OidcProperties.Client();
        client.setClientId(clientId);
        client.setRedirectUri(URI.create(
            "http://127.0.0.1:" + port + "/login/oauth2/code/" + registrationId
        ));
        client.setPostLogoutRedirectUri(URI.create("http://127.0.0.1:" + port + "/"));
        return client;
    }
}
