package com.ssolab.gallery.analytics;

import com.ssolab.gallery.api.GalleryDtos;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtworkAnalyticsService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final ArtworkAnalyticsQueryRepository repository;
    private final Clock clock;

    @Autowired
    public ArtworkAnalyticsService(ArtworkAnalyticsQueryRepository repository) {
        this(repository, Clock.systemUTC());
    }

    ArtworkAnalyticsService(ArtworkAnalyticsQueryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ArtworkAnalyticsDtos.Overview overview() {
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        return new ArtworkAnalyticsDtos.Overview(repository.metric(null), repository.metric(today),
            repository.metric(today.minusDays(6)), repository.metric(today.minusDays(29)),
            repository.daily(today.minusDays(29)), repository.topArtworks(), repository.surfaces(),
            repository.sources(), repository.referrers());
    }

    @Transactional(readOnly = true)
    public GalleryDtos.Page<ArtworkAnalyticsDtos.RecentView> recentViews(int page, int size) {
        return repository.recentViews(Math.max(0, page), Math.clamp(size, 1, 100));
    }
}
