package com.ssolab.gallery.api;

import com.ssolab.gallery.analytics.ArtworkAnalyticsDtos;
import com.ssolab.gallery.analytics.ArtworkAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(GalleryRoutes.STUDIO_API + "/analytics")
public class StudioAnalyticsController {
    private final ArtworkAnalyticsService analytics;

    public StudioAnalyticsController(ArtworkAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping
    ArtworkAnalyticsDtos.Overview overview() {
        return analytics.overview();
    }

    @GetMapping("/views")
    GalleryDtos.Page<ArtworkAnalyticsDtos.RecentView> recentViews(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return analytics.recentViews(page, size);
    }
}
