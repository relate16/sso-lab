package com.ssolab.gallery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gallery.analytics")
public record GalleryAnalyticsProperties(int dedupMinutes, int cookieMaxAgeDays) {
    public GalleryAnalyticsProperties {
        if (dedupMinutes < 1 || dedupMinutes > 1440) {
            throw new IllegalArgumentException("gallery analytics dedup minutes must be between 1 and 1440");
        }
        if (cookieMaxAgeDays < 1) {
            throw new IllegalArgumentException("gallery analytics cookie max age must be positive");
        }
    }
}
