package com.ssolab.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class GalleryServerPostgresqlIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("gallery.storage.root", () ->
            Path.of("build", "tmp", "gallery-postgresql-integration").toAbsolutePath().toString());
        registry.add("sso.oidc-client.registration-id", () -> "gallery-client");
        registry.add("sso.oidc-client.client-id", () -> "gallery-client");
        registry.add("sso.oidc-client.client-secret", () -> "gallery-test-secret-not-for-production");
        registry.add("sso.oidc-client.issuer", () -> "http://auth.example.test");
        registry.add("sso.oidc-client.authorization-uri", () ->
            "http://auth.example.test/oauth2/authorize");
        registry.add("sso.oidc-client.token-uri", () -> "http://auth.example.test/oauth2/token");
        registry.add("sso.oidc-client.jwk-set-uri", () -> "http://auth.example.test/oauth2/jwks");
        registry.add("sso.oidc-client.user-info-uri", () -> "http://auth.example.test/userinfo");
        registry.add("sso.oidc-client.redirect-uri", () ->
            "http://gallery.example.test/login/oauth2/code/gallery-client");
        registry.add("sso.oidc-client.post-logout-redirect-uri", () ->
            "http://gallery.example.test/");
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    HealthEndpoint healthEndpoint;

    @Test
    void appliesGalleryMigrationBeforeHibernateValidation() {
        Integer migrationCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gallery.flyway_schema_history WHERE version = '1' AND success",
            Integer.class
        );
        Set<String> tables = Set.copyOf(jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'gallery' AND table_name IN (?, ?, ?)",
            String.class,
            "artworks", "artwork_images", "inquiries"
        ));

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tables).containsExactlyInAnyOrder("artworks", "artwork_images", "inquiries");
        assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
    }
}
