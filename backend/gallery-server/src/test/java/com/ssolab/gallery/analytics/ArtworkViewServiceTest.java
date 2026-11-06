package com.ssolab.gallery.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssolab.gallery.artwork.ArtworkEntity;
import com.ssolab.gallery.artwork.ArtworkRepository;
import com.ssolab.gallery.config.GalleryAnalyticsProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ArtworkViewServiceTest {
    @Test
    void bindsJdbcSupportedTimestampsWhileReportingDatesUseSeoul() {
        var artworks = mock(ArtworkRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        var artwork = mock(ArtworkEntity.class);
        UUID publicId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(artworks.findPublishedByPublicId(publicId)).thenReturn(Optional.of(artwork));
        when(artwork.getId()).thenReturn(id);
        Instant now = Instant.parse("2026-11-03T15:01:27Z");
        var service = new ArtworkViewService(artworks, jdbc,
            new GalleryAnalyticsProperties(30, 365), Clock.fixed(now, ZoneOffset.UTC));

        service.record(publicId, new ArtworkAnalyticsDtos.ViewRequest(
            ArtworkViewSurface.WORKS_GRID, ArtworkEntrySource.DIRECT), "A".repeat(43),
            null, "gallery.example.test", "203.0.113.10");

        var parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), parameters.capture());
        Object[] bound = parameters.getValue();
        assertThat(bound[0]).isEqualTo(id);
        assertThat(bound[3]).isEqualTo(now.atOffset(ZoneOffset.UTC));
        assertThat(bound[10]).isEqualTo(now.atOffset(ZoneOffset.UTC));
        assertThat(bound[11]).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(bound[12]).isEqualTo("203.0.113.10");
        assertThat(bound).noneMatch(Instant.class::isInstance);
    }

    @Test
    void acceptsIpv4AndIpv6LiteralsButTreatsMissingOrInvalidAddressesAsUnknown() {
        assertThat(ArtworkViewService.normalizeIp("203.0.113.10")).isEqualTo("203.0.113.10");
        assertThat(ArtworkViewService.normalizeIp("2001:db8::1"))
            .isEqualTo("2001:db8:0:0:0:0:0:1");
        for (String value : new String[] { "", "localhost", "gallery.example.test",
            "999.1.2.3", "127.1", "2001:::1", "203.0.113.1, 198.51.100.1", "fe80::1%eth0" }) {
            assertThat(ArtworkViewService.normalizeIp(value)).as(value).isNull();
        }
        assertThat(ArtworkViewService.normalizeIp(null)).isNull();
    }
}
