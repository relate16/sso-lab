package com.ssolab.gallery.analytics;

import com.ssolab.gallery.api.GalleryDtos;
import java.time.LocalDate;
import java.util.List;

public interface ArtworkAnalyticsQueryRepository {
    ArtworkAnalyticsDtos.Metric metric(LocalDate from);
    List<ArtworkAnalyticsDtos.Daily> daily(LocalDate from);
    List<ArtworkAnalyticsDtos.TopArtwork> topArtworks();
    List<ArtworkAnalyticsDtos.Breakdown> surfaces();
    List<ArtworkAnalyticsDtos.Breakdown> sources();
    List<ArtworkAnalyticsDtos.Breakdown> referrers();
    GalleryDtos.Page<ArtworkAnalyticsDtos.RecentView> recentViews(int page, int size);
}
