package com.ssolab.gallery.artwork;

import com.ssolab.gallery.api.GalleryDtos;
import com.ssolab.gallery.api.GalleryNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicArtworkService {
    private final ArtworkRepository artworks;

    public PublicArtworkService(ArtworkRepository artworks) { this.artworks = artworks; }

    @Transactional(readOnly = true)
    public List<GalleryDtos.Artwork> home() {
        return artworks.findTop4ByPublishedTrueOrderByPublishedAtDesc().stream()
            .map(GalleryDtos.Artwork::from).toList();
    }

    @Transactional(readOnly = true)
    public GalleryDtos.Artwork detail(UUID publicId) {
        return GalleryDtos.Artwork.from(artworks.findByPublicIdAndPublishedTrue(publicId)
            .orElseThrow(() -> new GalleryNotFoundException("artwork not found")));
    }

    @Transactional(readOnly = true)
    public GalleryDtos.Page<GalleryDtos.Artwork> search(String query, SaleStatus saleStatus,
        BigDecimal minPrice, BigDecimal maxPrice, String sizeBand, String sort, int page, int size) {
        Specification<ArtworkEntity> spec = (root, ignored, cb) -> cb.isTrue(root.get("published"));
        if (query != null && !query.isBlank()) {
            String term = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, ignored, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), term),
                cb.like(cb.lower(root.get("description")), term)));
        }
        if (saleStatus != null) spec = spec.and((root, ignored, cb) -> cb.equal(root.get("saleStatus"), saleStatus));
        if (minPrice != null) spec = spec.and((root, ignored, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice));
        if (maxPrice != null) spec = spec.and((root, ignored, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice));
        spec = applySize(spec, sizeBand);
        Sort ordering = switch (sort == null ? "recent" : sort) {
            case "price-asc" -> Sort.by(Sort.Order.asc("price").nullsLast(), Sort.Order.asc("id"));
            case "price-desc" -> Sort.by(Sort.Order.desc("price").nullsLast(), Sort.Order.asc("id"));
            default -> Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.asc("displayOrder"));
        };
        var result = artworks.findAll(spec, PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 60), ordering));
        return new GalleryDtos.Page<>(result.stream().map(GalleryDtos.Artwork::from).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private Specification<ArtworkEntity> applySize(Specification<ArtworkEntity> spec, String sizeBand) {
        if (sizeBand == null || sizeBand.isBlank()) return spec;
        return switch (sizeBand.toUpperCase(Locale.ROOT)) {
            case "SMALL" -> spec.and((root, ignored, cb) -> cb.lessThanOrEqualTo(
                root.<BigDecimal>get("widthCm"), new BigDecimal("50"))).and((root, ignored, cb) ->
                    cb.lessThanOrEqualTo(root.<BigDecimal>get("heightCm"), new BigDecimal("50")));
            case "MEDIUM" -> spec.and((root, ignored, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.<BigDecimal>get("widthCm"), new BigDecimal("100")),
                cb.lessThanOrEqualTo(root.<BigDecimal>get("heightCm"), new BigDecimal("100")),
                cb.or(cb.greaterThan(root.<BigDecimal>get("widthCm"), new BigDecimal("50")),
                    cb.greaterThan(root.<BigDecimal>get("heightCm"), new BigDecimal("50")))));
            case "LARGE" -> spec.and((root, ignored, cb) -> cb.or(
                cb.greaterThan(root.<BigDecimal>get("widthCm"), new BigDecimal("100")),
                cb.greaterThan(root.<BigDecimal>get("heightCm"), new BigDecimal("100"))));
            default -> throw new com.ssolab.gallery.api.GalleryValidationException("invalid size band");
        };
    }
}
