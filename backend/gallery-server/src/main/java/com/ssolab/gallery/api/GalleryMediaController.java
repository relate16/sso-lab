package com.ssolab.gallery.api;

import com.ssolab.gallery.artwork.ArtworkImageEntity;
import com.ssolab.gallery.artwork.ArtworkImageRepository;
import com.ssolab.gallery.storage.ArtworkImageStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GalleryMediaController {
    private final ArtworkImageRepository images;
    private final ArtworkImageStorage storage;
    public GalleryMediaController(ArtworkImageRepository images, ArtworkImageStorage storage) {
        this.images = images; this.storage = storage;
    }
    @GetMapping(GalleryRoutes.PUBLIC_API + "/media/{storageKey}/{variant}")
    ResponseEntity<FileSystemResource> image(@PathVariable String storageKey,
        @PathVariable String variant) throws IOException {
        ArtworkImageEntity image = images.findByStorageKeyAndArtworkPublishedTrue(storageKey)
            .orElseThrow(() -> new GalleryNotFoundException("image not found"));
        String path = switch (variant) {
            case "web" -> image.getWebImagePath();
            case "thumbnail" -> image.getThumbnailPath();
            default -> throw new GalleryNotFoundException("image not found");
        };
        Path resolved = storage.resolve(path);
        if (!Files.isRegularFile(resolved)) throw new GalleryNotFoundException("image not found");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable())
            .body(new FileSystemResource(resolved));
    }

    @GetMapping(GalleryRoutes.STUDIO_API + "/media/{storageKey}/{variant}")
    ResponseEntity<FileSystemResource> studioImage(@PathVariable String storageKey,
        @PathVariable String variant) throws IOException {
        ArtworkImageEntity image = images.findByStorageKey(storageKey)
            .orElseThrow(() -> new GalleryNotFoundException("image not found"));
        String path = switch (variant) {
            case "web" -> image.getWebImagePath();
            case "thumbnail" -> image.getThumbnailPath();
            default -> throw new GalleryNotFoundException("image not found");
        };
        Path resolved = storage.resolve(path);
        if (!Files.isRegularFile(resolved)) throw new GalleryNotFoundException("image not found");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.noStore()).body(new FileSystemResource(resolved));
    }
}
