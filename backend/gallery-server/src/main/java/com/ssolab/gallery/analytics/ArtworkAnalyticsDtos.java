package com.ssolab.gallery.analytics;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ArtworkAnalyticsDtos {
    private ArtworkAnalyticsDtos() { }

    public record ViewRequest(@NotNull ArtworkViewSurface surface,
        @NotNull ArtworkEntrySource entrySource) { }

    public record Metric(long views, long visitors) { }
    public record Daily(LocalDate date, long views, long visitors) { }
    public record Breakdown(String key, long views, long visitors) { }
    public record TopArtwork(UUID id, String title, boolean archived, long views,
        long visitors) { }
    public record RecentView(UUID id, UUID artworkId, String artworkTitle, boolean archived,
        ArtworkViewSurface surface, Instant viewedAt, String visitorIp) { }
    public record Overview(Metric lifetime, Metric today, Metric last7Days,
        Metric last30Days, List<Daily> daily, List<TopArtwork> topArtworks,
        List<Breakdown> surfaces, List<Breakdown> sources,
        List<Breakdown> referrers) { }
}
