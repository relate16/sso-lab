package com.ssolab.gallery.artwork;

import com.ssolab.gallery.api.GalleryDtos;
import com.ssolab.gallery.api.GalleryNotFoundException;
import com.ssolab.gallery.api.GalleryValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicArtworkService {
    private final ArtworkRepository artworks;

    public PublicArtworkService(ArtworkRepository artworks) { this.artworks = artworks; }

    @Transactional(readOnly = true)
    public List<GalleryDtos.Artwork> home() {
        return artworks.findLatestPublished(4).stream()
            .map(GalleryDtos.Artwork::from).toList();
    }

    @Transactional(readOnly = true)
    public GalleryDtos.Artwork detail(UUID publicId) {
        return GalleryDtos.Artwork.from(artworks.findPublishedByPublicId(publicId)
            .orElseThrow(() -> new GalleryNotFoundException("artwork not found")));
    }

    @Transactional(readOnly = true)
    public GalleryDtos.Page<GalleryDtos.Artwork> search(String query, SaleStatus saleStatus,
        BigDecimal minPrice, BigDecimal maxPrice, String sizeBand, String sort, int page, int size) {
        var criteria = new ArtworkSearchCriteria(query, saleStatus, minPrice, maxPrice,
            sizeBand(sizeBand), order(sort));
        var result = artworks.search(criteria,
            PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 60)));
        return new GalleryDtos.Page<>(result.stream().map(GalleryDtos.Artwork::from).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private ArtworkSearchCriteria.SizeBand sizeBand(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ArtworkSearchCriteria.SizeBand.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new GalleryValidationException("invalid size band");
        }
    }

    private ArtworkSearchCriteria.Order order(String value) {
        if (value == null || value.isBlank() || value.equals("recent")) {
            return ArtworkSearchCriteria.Order.RECENT;
        }
        return switch (value) {
            case "price-asc" -> ArtworkSearchCriteria.Order.PRICE_ASC;
            case "price-desc" -> ArtworkSearchCriteria.Order.PRICE_DESC;
            default -> ArtworkSearchCriteria.Order.RECENT;
        };
    }
}
