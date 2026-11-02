package com.ssolab.gallery.api;

import com.ssolab.gallery.analytics.ArtworkAnalyticsDtos;
import com.ssolab.gallery.analytics.ArtworkAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
