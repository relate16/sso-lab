package com.ssolab.gallery.api;

import com.ssolab.gallery.artwork.ArtworkEntity;
import com.ssolab.gallery.artwork.ArtworkImageEntity;
import com.ssolab.gallery.artwork.FrameType;
import com.ssolab.gallery.artwork.SaleStatus;
import com.ssolab.gallery.inquiry.InquiryEntity;
import com.ssolab.gallery.inquiry.InquiryStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class GalleryDtos {
    private GalleryDtos() { }

    public record Image(UUID id, String storageKey, String webUrl, String thumbnailUrl,
        int widthPx, int heightPx, int sortOrder, boolean primary) {
        public static Image from(ArtworkImageEntity image) {
            String base = GalleryRoutes.PUBLIC_API + "/media/" + image.getStorageKey();
            return new Image(image.getId(), image.getStorageKey(), base + "/web",
                base + "/thumbnail", image.getWidthPx(), image.getHeightPx(),
                image.getSortOrder(), image.isPrimary());
        }
    }

    public record Artwork(UUID id, String title, String description, Integer year,
        String material, BigDecimal widthCm, BigDecimal heightCm, BigDecimal price,
        SaleStatus saleStatus, FrameType frameType, boolean published, boolean featured,
        int displayOrder, Instant publishedAt, List<Image> images) {
        public static Artwork from(ArtworkEntity artwork) {
            List<Image> images = artwork.getImages().stream()
                .sorted(Comparator.comparingInt(ArtworkImageEntity::getSortOrder))
                .map(Image::from).toList();
            return new Artwork(artwork.getPublicId(), artwork.getTitle(), artwork.getDescription(),
                artwork.getYear(), artwork.getMaterial(), artwork.getWidthCm(),
                artwork.getHeightCm(), artwork.getPrice(), artwork.getSaleStatus(),
                artwork.getFrameType(), artwork.isPublished(), artwork.isFeatured(),
                artwork.getDisplayOrder(), artwork.getPublishedAt(), images);
        }
    }

    public record Page<T>(List<T> content, int page, int size, long totalElements,
        int totalPages) { }

    public record ArtworkUpsert(
        @NotBlank @Size(max = 160) String title,
        @NotNull @Size(max = 5000) String description,
        Integer year,
        @Size(max = 240) String material,
        @NotNull @DecimalMin("0.01") BigDecimal widthCm,
        @NotNull @DecimalMin("0.01") BigDecimal heightCm,
        @DecimalMin("0.00") BigDecimal price,
        @NotNull SaleStatus saleStatus,
        @NotNull FrameType frameType,
        boolean published,
        boolean featured
    ) { }

    public record ReorderItem(@NotNull UUID id, int displayOrder) { }
    public record ReorderRequest(@NotEmpty @Size(max = 500) List<ReorderItem> items) { }
    public record PrimaryImageRequest(@NotNull UUID imageId) { }

    public record InquiryCreate(
        @NotNull UUID artworkId,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @Pattern(regexp = "^[0-9+() .-]{0,40}$") String phone,
        @NotBlank @Size(max = 3000) String message,
        boolean privacyAgreed
    ) { }

    public record Inquiry(UUID id, UUID artworkId, String artworkTitle, String name,
        String email, String phone, String message, InquiryStatus status, Instant createdAt) {
        public static Inquiry from(InquiryEntity inquiry) {
            return new Inquiry(inquiry.getId(), inquiry.getArtwork().getPublicId(),
                inquiry.getArtwork().getTitle(), inquiry.getName(), inquiry.getEmail(),
                inquiry.getPhone(), inquiry.getMessage(), inquiry.getStatus(),
                inquiry.getCreatedAt());
        }
    }

    public record InquiryStatusUpdate(@NotNull InquiryStatus status) { }
}
