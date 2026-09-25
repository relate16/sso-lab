package com.ssolab.gallery.artwork;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "artwork_images", schema = "gallery")
public class ArtworkImageEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artwork_id", nullable = false)
    private ArtworkEntity artwork;

    @Column(name = "storage_key", nullable = false, unique = true, length = 80)
    private String storageKey;

    @Column(name = "web_image_path", nullable = false, length = 240)
    private String webImagePath;

    @Column(name = "thumbnail_path", nullable = false, length = 240)
    private String thumbnailPath;

    @Column(name = "original_path", nullable = false, length = 240)
    private String originalPath;

    @Column(name = "original_filename", nullable = false, length = 240)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "width_px", nullable = false)
    private int widthPx;

    @Column(name = "height_px", nullable = false)
    private int heightPx;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ArtworkImageEntity() { }

    public UUID getId() { return id; }
    public ArtworkEntity getArtwork() { return artwork; }
    public String getStorageKey() { return storageKey; }
    public String getWebImagePath() { return webImagePath; }
    public String getThumbnailPath() { return thumbnailPath; }
    public String getOriginalPath() { return originalPath; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public int getWidthPx() { return widthPx; }
    public int getHeightPx() { return heightPx; }
    public int getSortOrder() { return sortOrder; }
    public boolean isPrimary() { return primary; }
    public Instant getCreatedAt() { return createdAt; }
}
