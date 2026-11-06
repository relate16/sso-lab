package com.ssolab.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssolab.gallery.analytics.ArtworkAnalyticsDtos;
import com.ssolab.gallery.analytics.ArtworkAnalyticsQueryRepository;
import com.ssolab.gallery.analytics.ArtworkEntrySource;
import com.ssolab.gallery.analytics.ArtworkViewEventEntity;
import com.ssolab.gallery.analytics.ArtworkViewSurface;
import com.ssolab.gallery.artwork.ArtworkEntity;
import com.ssolab.gallery.artwork.FrameType;
import com.ssolab.gallery.artwork.SaleStatus;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;

/** Same QueryDSL analytics assertions on H2 locally and migrated PostgreSQL in CI. */
abstract class GalleryAnalyticsRepositoryContract extends GalleryQueryRepositoryContract {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    @Autowired ArtworkAnalyticsQueryRepository analyticsQueries;

    @Test
    void visitorHashJdbcMappingMatchesThePhysicalCharColumn() {
        var factory = entityManager.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class);
        var entity = factory.getRuntimeMetamodels().getMappingMetamodel()
            .getEntityDescriptor(ArtworkViewEventEntity.class);
        var attribute = (BasicValuedModelPart) entity.findAttributeMapping("visitorHash");
        int mappedType = attribute.getJdbcMapping().getJdbcType().getJdbcTypeCode();

        int databaseType = entityManager.unwrap(Session.class).doReturningWork(connection -> {
            try (var columns = connection.getMetaData().getColumns(null, null, "%", "%")) {
                while (columns.next()) {
                    if ("gallery".equalsIgnoreCase(columns.getString("TABLE_SCHEM"))
                        && "artwork_view_events".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && "visitor_hash".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                        return columns.getInt("DATA_TYPE");
                    }
                }
            }
            throw new AssertionError("visitor_hash column not found");
        });
        assertThat(databaseType).isEqualTo(Types.CHAR);
        assertThat(mappedType).as("visitor_hash Hibernate JDBC type must match the DB column")
            .isEqualTo(databaseType);
    }

    @Test
    void emptyAnalyticsReturnsZeroCountsAndEmptyLists() {
        assertThat(analyticsQueries.metric(null)).isEqualTo(new ArtworkAnalyticsDtos.Metric(0, 0));
        assertThat(analyticsQueries.metric(TODAY)).isEqualTo(new ArtworkAnalyticsDtos.Metric(0, 0));
        assertThat(analyticsQueries.daily(TODAY)).isEmpty();
        assertThat(analyticsQueries.topArtworks()).isEmpty();
        assertThat(analyticsQueries.surfaces()).isEmpty();
        assertThat(analyticsQueries.sources()).isEmpty();
        assertThat(analyticsQueries.referrers()).isEmpty();
        assertThat(analyticsQueries.recentViews(0, 20).totalElements()).isZero();
        assertThat(analyticsQueries.recentViews(0, 20).content()).isEmpty();
    }

    @Test
    void aggregatesDistinctVisitorsAcrossDatesWorksAndSurfacesWithoutDroppingArchivedWorks() {
        var alpha = analyticsArtwork("Alpha");
        alpha.archive(Instant.now());
        var beta = analyticsArtwork("Beta");
        entityManager.persist(event(alpha, "a", TODAY.minusDays(30), ArtworkViewSurface.HOME_CAROUSEL, null, null));
        entityManager.persist(event(alpha, "a", TODAY.minusDays(29), ArtworkViewSurface.HOME_CAROUSEL, null, null));
        entityManager.persist(event(beta, "a", TODAY.minusDays(6), ArtworkViewSurface.WORKS_GRID, "example.test", null));
        entityManager.persist(event(alpha, "a", TODAY.minusDays(1), ArtworkViewSurface.WORKS_LIGHTBOX, "example.test", null));
        entityManager.persist(event(alpha, "b", TODAY, ArtworkViewSurface.WORKS_GRID, null, null));
        entityManager.persist(event(beta, "a", TODAY, ArtworkViewSurface.WORKS_GRID, null, null));
        entityManager.flush();
        entityManager.clear();

        assertThat(analyticsQueries.metric(null)).isEqualTo(new ArtworkAnalyticsDtos.Metric(6, 2));
        assertThat(analyticsQueries.metric(TODAY)).isEqualTo(new ArtworkAnalyticsDtos.Metric(2, 2));
        assertThat(analyticsQueries.metric(TODAY.minusDays(6)))
            .isEqualTo(new ArtworkAnalyticsDtos.Metric(4, 2));
        assertThat(analyticsQueries.metric(TODAY.minusDays(29)))
            .isEqualTo(new ArtworkAnalyticsDtos.Metric(5, 2));
        assertThat(analyticsQueries.daily(TODAY.minusDays(29))).containsExactly(
            new ArtworkAnalyticsDtos.Daily(TODAY.minusDays(29), 1, 1),
            new ArtworkAnalyticsDtos.Daily(TODAY.minusDays(6), 1, 1),
            new ArtworkAnalyticsDtos.Daily(TODAY.minusDays(1), 1, 1),
            new ArtworkAnalyticsDtos.Daily(TODAY, 2, 2));
        assertThat(analyticsQueries.topArtworks()).containsExactly(
            new ArtworkAnalyticsDtos.TopArtwork(alpha.getPublicId(), "Alpha", true, 4, 2),
            new ArtworkAnalyticsDtos.TopArtwork(beta.getPublicId(), "Beta", false, 2, 1));
        assertThat(analyticsQueries.surfaces()).containsExactly(
            new ArtworkAnalyticsDtos.Breakdown("WORKS_GRID", 3, 2),
            new ArtworkAnalyticsDtos.Breakdown("HOME_CAROUSEL", 2, 1),
            new ArtworkAnalyticsDtos.Breakdown("WORKS_LIGHTBOX", 1, 1));
        assertThat(analyticsQueries.sources()).containsExactly(
            new ArtworkAnalyticsDtos.Breakdown("DIRECT", 6, 2));
        assertThat(analyticsQueries.referrers()).containsExactly(
            new ArtworkAnalyticsDtos.Breakdown("직접 접속", 4, 2),
            new ArtworkAnalyticsDtos.Breakdown("example.test", 2, 1));
    }

    @Test
    void recentQueryPagesStableTiesAndPreservesIpAndArchivedFlags() throws Exception {
        var artwork = analyticsArtwork("Archived work");
        artwork.archive(Instant.now());
        var oldest = event(artwork, "a", TODAY.minusDays(1), ArtworkViewSurface.WORKS_GRID, null, null);
        var second = event(artwork, "b", TODAY, ArtworkViewSurface.DETAIL_SCENE, null, "203.0.113.10");
        var newest = event(artwork, "c", TODAY, ArtworkViewSurface.HOME_EXHIBITION, null, "2001:db8::1");
        ReflectionTestUtils.setField(oldest, "id", new UUID(0, 1));
        ReflectionTestUtils.setField(second, "id", new UUID(0, 2));
        ReflectionTestUtils.setField(newest, "id", new UUID(0, 3));
        entityManager.persist(oldest);
        entityManager.persist(second);
        entityManager.persist(newest);
        entityManager.flush();
        entityManager.clear();

        var first = analyticsQueries.recentViews(0, 2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.content()).extracting(ArtworkAnalyticsDtos.RecentView::id)
            .containsExactly(new UUID(0, 3), new UUID(0, 2));
        assertThat(first.content().getFirst().visitorIp())
            .isEqualTo(InetAddress.getByName("2001:db8::1").getHostAddress());
        assertThat(first.content().get(1).visitorIp()).isEqualTo("203.0.113.10");
        assertThat(first.content()).allSatisfy(row -> {
            assertThat(row.artworkId()).isEqualTo(artwork.getPublicId());
            assertThat(row.artworkTitle()).isEqualTo("Archived work");
            assertThat(row.archived()).isTrue();
        });
        var last = analyticsQueries.recentViews(1, 2);
        assertThat(last.content()).hasSize(1);
        assertThat(last.content().getFirst().visitorIp()).isNull();
        assertThat(analyticsQueries.recentViews(2, 2).content()).isEmpty();
    }

    private ArtworkEntity analyticsArtwork(String title) {
        return artworks.save(new ArtworkEntity(title, "Description", 2026, "Oil",
            BigDecimal.TEN, BigDecimal.TEN, null, SaleStatus.NOT_FOR_SALE,
            FrameType.NONE, false, 0, Instant.now()));
    }

    private ArtworkViewEventEntity event(ArtworkEntity artwork, String visitor, LocalDate date,
        ArtworkViewSurface surface, String referrer, String ip) {
        var event = BeanUtils.instantiateClass(ArtworkViewEventEntity.class);
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "artwork", artwork);
        ReflectionTestUtils.setField(event, "visitorHash", visitor.repeat(64));
        ReflectionTestUtils.setField(event, "surface", surface);
        ReflectionTestUtils.setField(event, "entrySource", ArtworkEntrySource.DIRECT);
        ReflectionTestUtils.setField(event, "referrerHost", referrer);
        ReflectionTestUtils.setField(event, "viewedDate", date);
        ReflectionTestUtils.setField(event, "viewedAt", date.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());
        try {
            ReflectionTestUtils.setField(event, "visitorIp", ip == null ? null : InetAddress.getByName(ip));
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException(exception);
        }
        return event;
    }
}
