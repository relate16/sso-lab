package com.ssolab.gallery.artwork;

import com.ssolab.gallery.api.GalleryDtos;
import com.ssolab.gallery.api.GalleryNotFoundException;
import com.ssolab.gallery.api.GalleryValidationException;
import com.ssolab.gallery.storage.ArtworkImageStorage;
import com.ssolab.gallery.storage.StoredArtworkImage;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StudioArtworkService {
    private final ArtworkRepository artworks;
    private final ArtworkImageRepository images;
    private final ArtworkImageStorage storage;
    private final Clock clock;

    @Autowired
    public StudioArtworkService(ArtworkRepository artworks, ArtworkImageRepository images,
        ArtworkImageStorage storage) {
        this(artworks, images, storage, Clock.systemUTC());
    }

    StudioArtworkService(ArtworkRepository artworks, ArtworkImageRepository images,
        ArtworkImageStorage storage, Clock clock) {
        this.artworks = artworks; this.images = images; this.storage = storage; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GalleryDtos.Artwork> list() {
        return artworks.findAll(Sort.by("displayOrder", "createdAt")).stream()
            .map(GalleryDtos.Artwork::from).toList();
    }

    @Transactional(readOnly = true)
    public GalleryDtos.Artwork get(UUID publicId) { return GalleryDtos.Artwork.from(find(publicId)); }

    @Transactional
    public GalleryDtos.Artwork create(GalleryDtos.ArtworkUpsert request) {
        if (request.published()) throw new GalleryValidationException("upload an image before publishing");
        int nextOrder = Math.toIntExact(Math.min(Integer.MAX_VALUE, artworks.count()));
        ArtworkEntity artwork = new ArtworkEntity(request.title().trim(), request.description(),
            request.year(), trimToNull(request.material()), request.widthCm(), request.heightCm(),
            request.price(), request.saleStatus(), request.frameType(), request.featured(), nextOrder,
            clock.instant());
        return GalleryDtos.Artwork.from(artworks.save(artwork));
    }

    @Transactional
    public GalleryDtos.Artwork update(UUID publicId, GalleryDtos.ArtworkUpsert request) {
        ArtworkEntity artwork = find(publicId);
        if (request.published() && artwork.getImages().stream().noneMatch(ArtworkImageEntity::isPrimary)) {
            throw new GalleryValidationException("a primary image is required before publishing");
        }
        artwork.update(request.title().trim(), request.description(), request.year(),
            trimToNull(request.material()), request.widthCm(), request.heightCm(), request.price(),
            request.saleStatus(), request.frameType(), request.featured(), request.published(),
            clock.instant());
        return GalleryDtos.Artwork.from(artwork);
    }

    @Transactional
    public void delete(UUID publicId) {
        ArtworkEntity artwork = find(publicId);
        List<StoredArtworkImage> stored = artwork.getImages().stream().map(this::stored).toList();
        artworks.delete(artwork);
        artworks.flush();
        stored.forEach(image -> { try { storage.delete(image); } catch (IOException ignored) { } });
    }

    @Transactional
    public void reorder(GalleryDtos.ReorderRequest request) {
        List<UUID> requested = request.items().stream().map(GalleryDtos.ReorderItem::id).toList();
        if (requested.stream().distinct().count() != requested.size()) throw new GalleryValidationException("duplicate artwork");
        var found = artworks.findAll().stream().collect(java.util.stream.Collectors.toMap(ArtworkEntity::getPublicId, a -> a));
        if (!found.keySet().containsAll(requested)) throw new GalleryNotFoundException("artwork not found");
        Instant now = clock.instant();
        request.items().forEach(item -> found.get(item.id()).reorder(item.displayOrder(), now));
    }

    @Transactional
    public GalleryDtos.Image upload(UUID publicId, MultipartFile file) throws IOException {
        ArtworkEntity artwork = find(publicId);
        StoredArtworkImage stored = storage.store(file);
        boolean primary = artwork.getImages().isEmpty();
        ArtworkImageEntity image = new ArtworkImageEntity(artwork, stored.storageKey(),
            stored.originalPath(), stored.webImagePath(), stored.thumbnailPath(),
            safeFilename(file.getOriginalFilename()), stored.contentType(), stored.widthPx(),
            stored.heightPx(), artwork.getImages().size(), primary, clock.instant());
        artwork.addImage(image);
        return GalleryDtos.Image.from(images.save(image));
    }

    @Transactional
    public void makePrimary(UUID publicId, UUID imageId) {
        ArtworkEntity artwork = find(publicId);
        ArtworkImageEntity selected = artwork.getImages().stream().filter(i -> i.getId().equals(imageId))
            .findFirst().orElseThrow(() -> new GalleryNotFoundException("image not found"));
        artwork.getImages().forEach(ArtworkImageEntity::clearPrimary);
        selected.makePrimary();
    }

    @Transactional
    public void deleteImage(UUID publicId, UUID imageId) throws IOException {
        ArtworkEntity artwork = find(publicId);
        ArtworkImageEntity image = artwork.getImages().stream().filter(i -> i.getId().equals(imageId))
            .findFirst().orElseThrow(() -> new GalleryNotFoundException("image not found"));
        if (artwork.isPublished() && image.isPrimary()) throw new GalleryValidationException("unpublish before removing the primary image");
        artwork.removeImage(image);
        images.delete(image);
        images.flush();
        storage.delete(stored(image));
    }

    private ArtworkEntity find(UUID publicId) {
        return artworks.findByPublicId(publicId)
            .orElseThrow(() -> new GalleryNotFoundException("artwork not found"));
    }

    private StoredArtworkImage stored(ArtworkImageEntity image) {
        return new StoredArtworkImage(image.getStorageKey(), image.getOriginalPath(),
            image.getWebImagePath(), image.getThumbnailPath(), image.getContentType(),
            image.getWidthPx(), image.getHeightPx());
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "upload";
        String base = java.nio.file.Path.of(filename).getFileName().toString();
        return base.length() > 240 ? base.substring(base.length() - 240) : base;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
