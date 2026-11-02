package com.ssolab.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssolab.gallery.artwork.ArtworkEntity;
import com.ssolab.gallery.artwork.ArtworkImageEntity;
import com.ssolab.gallery.artwork.ArtworkImageRepository;
import com.ssolab.gallery.artwork.ArtworkRepository;
import com.ssolab.gallery.artwork.ArtworkSearchCriteria;
import com.ssolab.gallery.artwork.FrameType;
import com.ssolab.gallery.artwork.SaleStatus;
import com.ssolab.gallery.inquiry.InquiryEntity;
import com.ssolab.gallery.inquiry.InquiryRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
abstract class GalleryQueryRepositoryContract {
    private static final Instant NOW = Instant.parse("2026-11-02T00:00:00Z");
    @Autowired ArtworkRepository artworks;
    @Autowired ArtworkImageRepository images;
    @Autowired InquiryRepository inquiries;
    @Autowired EntityManager entityManager;

    @Test
    void publicQueriesExcludeDraftsAndArchivedWorksAndLoadImages() {
        var visible = artwork("Visible", 40, 60, "100", true, 0);
        image(visible, "public-image");
        var draft = artwork("Draft", 40, 60, "100", false, 1);
        var archived = artwork("Archived", 40, 60, "100", true, 2);
        archived.archive(NOW);
        flushAndClear();

        assertThat(artworks.findLatestPublished(4)).extracting(ArtworkEntity::getId)
            .containsExactly(visible.getId());
        var loaded = artworks.findPublishedByPublicId(visible.getPublicId()).orElseThrow();
        assertThat(Hibernate.isInitialized(loaded.getImages())).isTrue();
        assertThat(loaded.getImages()).hasSize(1);
        assertThat(artworks.findPublishedByPublicId(draft.getPublicId())).isEmpty();
        assertThat(artworks.findPublishedByPublicId(archived.getPublicId())).isEmpty();
        assertThat(artworks.findByPublicId(archived.getPublicId())).isPresent();
    }

    @Test
    void searchCombinesTextStatusPriceAndSize() {
        var match = artwork("Winter Light", 50, 100, "200", true, 0);
        artwork("Summer", 50, 100, "200", true, 1);
        artwork("Winter expensive", 50, 100, "900", true, 2);
        artwork("Winter large", 101, 100, "200", true, 3);
        var sold = artwork("Winter sold", 50, 100, "200", true, 4);
        sold.update(sold.getTitle(), sold.getDescription(), 2026, "Oil", sold.getWidthCm(),
            sold.getHeightCm(), sold.getPrice(), SaleStatus.SOLD, FrameType.NONE, false,
            new BigDecimal("0.5"), new BigDecimal("0.5"), BigDecimal.ONE, true, NOW);
        flushAndClear();

        var criteria = new ArtworkSearchCriteria("  WINTER  ", SaleStatus.AVAILABLE,
            new BigDecimal("100"), new BigDecimal("300"), ArtworkSearchCriteria.SizeBand.MEDIUM,
            ArtworkSearchCriteria.Order.RECENT);
        var page = artworks.search(criteria, PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(ArtworkEntity::getId).containsExactly(match.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void sizeBoundariesPartitionWorksWithoutOverlap() {
        var small = artwork("Small", 50, 50, "100", true, 0);
        var medium = artwork("Medium", 51, 100, "100", true, 1);
        var large = artwork("Large", 100, 101, "100", true, 2);
        flushAndClear();
        for (var entry : java.util.Map.of(
            ArtworkSearchCriteria.SizeBand.SMALL, small,
            ArtworkSearchCriteria.SizeBand.MEDIUM, medium,
            ArtworkSearchCriteria.SizeBand.LARGE, large).entrySet()) {
            var criteria = new ArtworkSearchCriteria(null, null, null, null, entry.getKey(),
                ArtworkSearchCriteria.Order.RECENT);
            assertThat(artworks.search(criteria, PageRequest.of(0, 10)).getContent())
                .extracting(ArtworkEntity::getId).containsExactly(entry.getValue().getId());
        }
    }

    @Test
    void pricePaginationKeepsNullsLastAndDoesNotMultiplyRowsForImages() {
        var cheap = artwork("Cheap", 30, 40, "100", true, 0);
        image(cheap, "cheap-1");
        image(cheap, "cheap-2");
        var expensive = artwork("Expensive", 30, 40, "200", true, 1);
        var noPrice = artwork("No price", 30, 40, null, true, 2);
        flushAndClear();
        var asc = criteria(ArtworkSearchCriteria.Order.PRICE_ASC);
        var first = artworks.search(asc, PageRequest.of(0, 1));
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.getContent()).extracting(ArtworkEntity::getId).containsExactly(cheap.getId());
        assertThat(first.getContent().getFirst().getImages()).hasSize(2);
        assertThat(artworks.search(asc, PageRequest.of(1, 1)).getContent())
            .extracting(ArtworkEntity::getId).containsExactly(expensive.getId());
        assertThat(artworks.search(asc, PageRequest.of(2, 1)).getContent())
            .extracting(ArtworkEntity::getId).containsExactly(noPrice.getId());
        assertThat(artworks.search(criteria(ArtworkSearchCriteria.Order.PRICE_DESC), PageRequest.of(0, 10)))
            .extracting(ArtworkEntity::getId)
            .containsExactly(expensive.getId(), cheap.getId(), noPrice.getId());
        assertThat(artworks.search(asc, PageRequest.of(9, 1)).getContent()).isEmpty();
    }

    @Test
    void studioListingIncludesDraftsAndArchivesAndReorderSelectsOnlyRequestedWorks() {
        var second = artwork("Second", 30, 40, null, false, 2);
        var first = artwork("First", 30, 40, null, true, 1);
        first.archive(NOW);
        flushAndClear();
        assertThat(artworks.findAllForStudio()).extracting(ArtworkEntity::getId)
            .containsExactly(first.getId(), second.getId());
        assertThat(artworks.findAllByPublicIds(List.of(second.getPublicId())))
            .extracting(ArtworkEntity::getId).containsExactly(second.getId());
        assertThat(artworks.findAllByPublicIds(List.of())).isEmpty();
    }

    @Test
    void mediaQueriesEnforcePublicVisibilityButAllowStudioAccess() {
        var visible = artwork("Visible", 30, 40, null, true, 0);
        var draft = artwork("Draft", 30, 40, null, false, 1);
        var archived = artwork("Archived", 30, 40, null, true, 2);
        archived.archive(NOW);
        image(visible, "visible");
        image(draft, "draft");
        image(archived, "archived");
        flushAndClear();
        assertThat(images.findPublishedByStorageKey("visible")).isPresent();
        assertThat(images.findPublishedByStorageKey("draft")).isEmpty();
        assertThat(images.findPublishedByStorageKey("archived")).isEmpty();
        assertThat(images.findByStorageKey("archived")).isPresent();
        assertThat(images.findByStorageKey("missing")).isEmpty();
    }

    @Test
    void inquiriesArePagedNewestFirstWithArtworkFetched() {
        var artwork = artwork("Artwork", 30, 40, null, true, 0);
        inquiries.save(new InquiryEntity(artwork, "Old", "old@example.test", null, "Old", NOW));
        var newest = inquiries.save(new InquiryEntity(artwork, "New", "new@example.test", null,
            "New", NOW.plusSeconds(1)));
        flushAndClear();
        var page = inquiries.findNewestFirst(PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(InquiryEntity::getId).containsExactly(newest.getId());
        assertThat(Hibernate.isInitialized(page.getContent().getFirst().getArtwork())).isTrue();
    }

    private ArtworkSearchCriteria criteria(ArtworkSearchCriteria.Order order) {
        return new ArtworkSearchCriteria(null, null, null, null, null, order);
    }

    private ArtworkEntity artwork(String title, int width, int height, String price,
        boolean published, int order) {
        var artwork = new ArtworkEntity(title, "Description", 2026, "Oil",
            BigDecimal.valueOf(width), BigDecimal.valueOf(height),
            price == null ? null : new BigDecimal(price), SaleStatus.AVAILABLE,
            FrameType.NONE, false, order, NOW);
        artwork.update(title, "Description", 2026, "Oil", artwork.getWidthCm(), artwork.getHeightCm(),
            artwork.getPrice(), SaleStatus.AVAILABLE, FrameType.NONE, false, new BigDecimal("0.5"),
            new BigDecimal("0.5"), BigDecimal.ONE, published, NOW.plusSeconds(order));
        return artworks.save(artwork);
    }

    private void image(ArtworkEntity artwork, String key) {
        artwork.addImage(new ArtworkImageEntity(artwork, key, "original.jpg", "web.jpg", "thumb.jpg",
            "image.jpg", "image/jpeg", 100, 100, artwork.getImages().size(),
            artwork.getImages().isEmpty(), NOW));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
