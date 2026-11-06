package com.ssolab.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssolab.gallery.analytics.ArtworkAnalyticsDtos;
import com.ssolab.gallery.analytics.ArtworkAnalyticsService;
import com.ssolab.gallery.analytics.ArtworkEntrySource;
import com.ssolab.gallery.analytics.ArtworkViewService;
import com.ssolab.gallery.analytics.ArtworkViewSurface;
import com.ssolab.gallery.artwork.ArtworkEntity;
import com.ssolab.gallery.artwork.FrameType;
import com.ssolab.gallery.artwork.SaleStatus;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
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
class GalleryServerPostgresqlIntegrationTest extends GalleryAnalyticsRepositoryContract {

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

    @Autowired ArtworkViewService views;
    @Autowired ArtworkAnalyticsService analytics;

    @Test
    void persistsViewsAndCountsUniqueVisitorsAcrossSurfacesAndArtworks() {
        ArtworkEntity first = publishedArtwork("First");
        ArtworkEntity second = publishedArtwork("Second");
        var grid = new ArtworkAnalyticsDtos.ViewRequest(
            ArtworkViewSurface.WORKS_GRID, ArtworkEntrySource.DIRECT);
        var lightbox = new ArtworkAnalyticsDtos.ViewRequest(
            ArtworkViewSurface.WORKS_LIGHTBOX, ArtworkEntrySource.WORKS_GRID);
        String visitor = "A".repeat(43);

        assertThat(views.record(first.getPublicId(), grid, visitor, null, "gallery.example.test", "203.0.113.1")).isTrue();
        assertThat(views.record(first.getPublicId(), grid, visitor, null, "gallery.example.test", "203.0.113.2")).isFalse();
        assertThat(views.record(first.getPublicId(), lightbox, visitor, null, "gallery.example.test", null)).isTrue();
        assertThat(views.record(first.getPublicId(), grid, "B".repeat(43), null, "gallery.example.test", null)).isTrue();
        assertThat(views.record(second.getPublicId(), grid, visitor, null, "gallery.example.test", null)).isTrue();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gallery.artwork_view_events", Long.class)).isEqualTo(4L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gallery.artwork_view_dedup", Long.class)).isEqualTo(4L);
        var overview = analytics.overview();
        assertThat(overview.lifetime().views()).isEqualTo(4);
        assertThat(overview.lifetime().visitors()).isEqualTo(2);
        assertThat(overview.topArtworks()).hasSize(2);
    }

    @Test
    void countsAgainOnlyWhenTheDedupWindowHasElapsed() {
        ArtworkEntity artwork = publishedArtwork("Window test");
        var request = new ArtworkAnalyticsDtos.ViewRequest(
            ArtworkViewSurface.DETAIL_SCENE, ArtworkEntrySource.DIRECT);
        String visitor = "C".repeat(43);
        assertThat(views.record(artwork.getPublicId(), request, visitor, null, "gallery.example.test", null)).isTrue();
        OffsetDateTime firstCountedAt = jdbcTemplate.queryForObject(
            "SELECT last_counted_at FROM gallery.artwork_view_dedup WHERE artwork_id = ?",
            (row, index) -> row.getObject("last_counted_at", OffsetDateTime.class), artwork.getId());

        jdbcTemplate.update("UPDATE gallery.artwork_view_dedup SET last_counted_at = ? WHERE artwork_id = ?",
            firstCountedAt.minusMinutes(29), artwork.getId());
        assertThat(views.record(artwork.getPublicId(), request, visitor, null, "gallery.example.test", null)).isFalse();
        jdbcTemplate.update("UPDATE gallery.artwork_view_dedup SET last_counted_at = ? WHERE artwork_id = ?",
            firstCountedAt.minusMinutes(31), artwork.getId());
        assertThat(views.record(artwork.getPublicId(), request, visitor, null, "gallery.example.test", null)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gallery.artwork_view_events WHERE artwork_id = ?",
            Long.class, artwork.getId())).isEqualTo(2L);
    }

    @Test
    void doesNotPersistViewsForUnpublishedOrArchivedWorks() {
        ArtworkEntity draft = publishedArtwork("Draft");
        draft.update("Draft", "Description", 2026, "Oil", BigDecimal.TEN, BigDecimal.TEN,
            null, SaleStatus.NOT_FOR_SALE, FrameType.NONE, false, new BigDecimal("0.5"),
            new BigDecimal("0.5"), BigDecimal.ONE, false, Instant.now());
        ArtworkEntity archived = publishedArtwork("Archived");
        archived.archive(Instant.now());
        var request = new ArtworkAnalyticsDtos.ViewRequest(
            ArtworkViewSurface.WORKS_GRID, ArtworkEntrySource.DIRECT);
        assertThat(views.record(draft.getPublicId(), request, "D".repeat(43), null, "gallery.example.test", null)).isFalse();
        assertThat(views.record(archived.getPublicId(), request, "D".repeat(43), null, "gallery.example.test", null)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gallery.artwork_view_events", Long.class)).isZero();
    }

    @Test
    void storesRawIpAndPagesRecentViewsIncludingUnknownIpAndArchivedWorks() {
        ArtworkEntity artwork = publishedArtwork("IP history");
        var request = new ArtworkAnalyticsDtos.ViewRequest(
            ArtworkViewSurface.WORKS_GRID, ArtworkEntrySource.DIRECT);
        assertThat(views.record(artwork.getPublicId(), request, "E".repeat(43), null,
            "gallery.example.test", "203.0.113.10")).isTrue();
        assertThat(views.record(artwork.getPublicId(), request, "F".repeat(43), null,
            "gallery.example.test", "2001:db8::1")).isTrue();
        assertThat(views.record(artwork.getPublicId(), request, "G".repeat(43), null,
            "gallery.example.test", "not-an-ip")).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM gallery.artwork_view_events WHERE visitor_ip = '203.0.113.10'::inet
            """, Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM gallery.artwork_view_events WHERE visitor_ip = '2001:db8::1'::inet
            """, Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM gallery.artwork_view_events WHERE visitor_ip IS NULL
            """, Long.class)).isEqualTo(1);

        // A tie in viewed_at still needs a deterministic order across page boundaries.
        jdbcTemplate.update("UPDATE gallery.artwork_view_events SET viewed_at = ?",
            OffsetDateTime.parse("2026-11-05T01:00:00Z"));
        var expectedIds = jdbcTemplate.queryForList(
            "SELECT id FROM gallery.artwork_view_events ORDER BY viewed_at DESC, id DESC",
            java.util.UUID.class);
        artwork.archive(Instant.now());
        artworks.flush();

        var first = analytics.recentViews(0, 2);
        var second = analytics.recentViews(1, 2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.content()).extracting(ArtworkAnalyticsDtos.RecentView::id)
            .containsExactlyElementsOf(expectedIds.subList(0, 2));
        assertThat(second.content()).extracting(ArtworkAnalyticsDtos.RecentView::id)
            .containsExactly(expectedIds.get(2));
        assertThat(analytics.recentViews(0, 20).content())
            .allSatisfy(item -> {
                assertThat(item.artworkId()).isEqualTo(artwork.getPublicId());
                assertThat(item.artworkTitle()).isEqualTo("IP history");
                assertThat(item.archived()).isTrue();
            }).extracting(ArtworkAnalyticsDtos.RecentView::visitorIp)
            .containsExactlyInAnyOrder("203.0.113.10", "2001:db8:0:0:0:0:0:1", null);
        assertThat(analytics.recentViews(10, 2).content()).isEmpty();
        assertThat(analytics.recentViews(-1, 0).page()).isZero();
        assertThat(analytics.recentViews(-1, 0).size()).isEqualTo(1);
        assertThat(analytics.recentViews(0, 101).size()).isEqualTo(100);
        assertThat(analytics.overview().lifetime().views()).isEqualTo(3);
    }

    private ArtworkEntity publishedArtwork(String title) {
        Instant now = Instant.now();
        var artwork = new ArtworkEntity(title, "Description", 2026, "Oil",
            BigDecimal.TEN, BigDecimal.TEN, null, SaleStatus.NOT_FOR_SALE,
            FrameType.NONE, false, 0, now);
        artwork.update(title, "Description", 2026, "Oil", BigDecimal.TEN, BigDecimal.TEN,
            null, SaleStatus.NOT_FOR_SALE, FrameType.NONE, false, new BigDecimal("0.5"),
            new BigDecimal("0.5"), BigDecimal.ONE, true, now);
        return artworks.saveAndFlush(artwork);
    }

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
