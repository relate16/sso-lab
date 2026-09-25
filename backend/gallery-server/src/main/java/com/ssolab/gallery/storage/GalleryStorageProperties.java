package com.ssolab.gallery.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gallery.storage")
public record GalleryStorageProperties(Path root, long maxUploadBytes, int webMaxPixels,
    int thumbnailMaxPixels) {
    public GalleryStorageProperties {
        if (root == null) throw new IllegalArgumentException("gallery storage root is required");
        if (maxUploadBytes <= 0 || webMaxPixels <= 0 || thumbnailMaxPixels <= 0) {
            throw new IllegalArgumentException("gallery storage limits must be positive");
        }
    }
}
