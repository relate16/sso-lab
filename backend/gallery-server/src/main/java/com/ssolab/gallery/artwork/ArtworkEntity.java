package com.ssolab.gallery.artwork;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "artworks", schema = "gallery")
public class ArtworkEntity {
    @Id
    private UUID id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    @Column(name = "production_year")
    private Integer year;

    @Column(length = 240)
    private String material;

    @Column(name = "width_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal heightCm;

    @Column(precision = 14, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "sale_status", nullable = false, length = 24)
    private SaleStatus saleStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "frame_type", nullable = false, length = 24)
    private FrameType frameType;

    @Column(nullable = false)
    private boolean published;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "carousel_focal_x", nullable = false, precision = 5, scale = 4)
    private BigDecimal carouselFocalX;

    @Column(name = "carousel_focal_y", nullable = false, precision = 5, scale = 4)
    private BigDecimal carouselFocalY;

    @Column(name = "carousel_zoom", nullable = false, precision = 4, scale = 2)
    private BigDecimal carouselZoom;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "artwork", cascade = CascadeType.ALL, orphanRemoval = true,
        fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<ArtworkImageEntity> images = new ArrayList<>();

    protected ArtworkEntity() { }

    public ArtworkEntity(String title, String description, Integer year, String material,
        BigDecimal widthCm, BigDecimal heightCm, BigDecimal price, SaleStatus saleStatus,
        FrameType frameType, boolean featured, int displayOrder, Instant now) {
        this(title, description, year, material, widthCm, heightCm, price, saleStatus,
            frameType, featured, new BigDecimal("0.5"), new BigDecimal("0.5"), BigDecimal.ONE,
            displayOrder, now);
    }

    public ArtworkEntity(String title, String description, Integer year, String material,
        BigDecimal widthCm, BigDecimal heightCm, BigDecimal price, SaleStatus saleStatus,
        FrameType frameType, boolean featured, BigDecimal carouselFocalX,
        BigDecimal carouselFocalY, BigDecimal carouselZoom, int displayOrder, Instant now) {
        this.id = UUID.randomUUID();
        this.publicId = UUID.randomUUID();
        this.title = Objects.requireNonNull(title);
        this.description = Objects.requireNonNull(description);
        this.year = year;
        this.material = material;
        this.widthCm = Objects.requireNonNull(widthCm);
        this.heightCm = Objects.requireNonNull(heightCm);
        this.price = price;
        this.saleStatus = Objects.requireNonNull(saleStatus);
        this.frameType = Objects.requireNonNull(frameType);
        this.featured = featured;
        this.carouselFocalX = Objects.requireNonNull(carouselFocalX);
        this.carouselFocalY = Objects.requireNonNull(carouselFocalY);
        this.carouselZoom = Objects.requireNonNull(carouselZoom);
        this.displayOrder = displayOrder;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Integer getYear() { return year; }
    public String getMaterial() { return material; }
    public BigDecimal getWidthCm() { return widthCm; }
    public BigDecimal getHeightCm() { return heightCm; }
    public BigDecimal getPrice() { return price; }
    public SaleStatus getSaleStatus() { return saleStatus; }
    public FrameType getFrameType() { return frameType; }
    public boolean isPublished() { return published; }
    public boolean isFeatured() { return featured; }
    public BigDecimal getCarouselFocalX() { return carouselFocalX; }
    public BigDecimal getCarouselFocalY() { return carouselFocalY; }
    public BigDecimal getCarouselZoom() { return carouselZoom; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ArtworkImageEntity> getImages() { return Collections.unmodifiableList(images); }

    public void update(String title, String description, Integer year, String material,
        BigDecimal widthCm, BigDecimal heightCm, BigDecimal price, SaleStatus saleStatus,
        FrameType frameType, boolean featured, BigDecimal carouselFocalX,
        BigDecimal carouselFocalY, BigDecimal carouselZoom, boolean publish, Instant now) {
        this.title = Objects.requireNonNull(title);
        this.description = Objects.requireNonNull(description);
        this.year = year;
        this.material = material;
        this.widthCm = Objects.requireNonNull(widthCm);
        this.heightCm = Objects.requireNonNull(heightCm);
        this.price = price;
        this.saleStatus = Objects.requireNonNull(saleStatus);
        this.frameType = Objects.requireNonNull(frameType);
        this.featured = featured;
        this.carouselFocalX = Objects.requireNonNull(carouselFocalX);
        this.carouselFocalY = Objects.requireNonNull(carouselFocalY);
        this.carouselZoom = Objects.requireNonNull(carouselZoom);
        if (publish && !published) this.publishedAt = now;
        if (!publish) this.publishedAt = null;
        this.published = publish;
        this.updatedAt = Objects.requireNonNull(now);
    }

    public void addImage(ArtworkImageEntity image) { images.add(Objects.requireNonNull(image)); }
    public void removeImage(ArtworkImageEntity image) { images.remove(image); }
    public void reorder(int order, Instant now) { displayOrder = order; updatedAt = now; }
}
