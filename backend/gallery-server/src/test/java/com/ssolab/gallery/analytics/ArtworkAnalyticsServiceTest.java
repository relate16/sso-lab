package com.ssolab.gallery.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssolab.gallery.api.GalleryDtos;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArtworkAnalyticsServiceTest {
    @Test
    void calculatesSeoulDateAndInclusiveReportingWindows() {
        var repository = mock(ArtworkAnalyticsQueryRepository.class);
        // UTC is still November 4, but Seoul has crossed midnight into November 5.
        var service = new ArtworkAnalyticsService(repository,
            Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneOffset.UTC));
        var metric = new ArtworkAnalyticsDtos.Metric(4, 2);
        when(repository.metric(LocalDate.of(2026, 11, 5))).thenReturn(metric);
        var overview = service.overview();
        assertThat(overview.today()).isEqualTo(metric);
        verify(repository).metric(null);
        verify(repository).metric(LocalDate.of(2026, 11, 5));
        verify(repository).metric(LocalDate.of(2026, 10, 30));
        verify(repository).metric(LocalDate.of(2026, 10, 7));
        verify(repository).daily(LocalDate.of(2026, 10, 7));
    }

    @Test
    void boundsPagingBeforeCallingRepository() {
        var repository = mock(ArtworkAnalyticsQueryRepository.class);
        var service = new ArtworkAnalyticsService(repository);
        var empty = new GalleryDtos.Page<ArtworkAnalyticsDtos.RecentView>(List.of(), 0, 1, 0, 0);
        when(repository.recentViews(0, 1)).thenReturn(empty);
        assertThat(service.recentViews(-1, 0)).isEqualTo(empty);
        service.recentViews(3, 101);
        verify(repository).recentViews(3, 100);
    }
}
