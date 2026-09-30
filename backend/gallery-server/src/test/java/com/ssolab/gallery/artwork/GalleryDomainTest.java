package com.ssolab.gallery.artwork;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GalleryDomainTest {
    @Test
    void createsArtworkWithSeparateInternalAndPublicIdentifiers() {
        ArtworkEntity artwork = new ArtworkEntity("Winter Orbit", "", 2026, "Ink",
            new BigDecimal("40"), new BigDecimal("30"), null,
            SaleStatus.NOT_FOR_SALE, FrameType.MAT_BOARD, false, new BigDecimal("0.5"),
            new BigDecimal("0.5"), BigDecimal.ONE, 0, Instant.EPOCH);

        assertThat(artwork.getId()).isNotEqualTo(artwork.getPublicId());
        assertThat(artwork.getFrameType()).isEqualTo(FrameType.MAT_BOARD);
        assertThat(artwork.getSaleStatus()).isEqualTo(SaleStatus.NOT_FOR_SALE);
        assertThat(artwork.getCarouselFocalX()).isEqualByComparingTo("0.5");
        assertThat(artwork.getCarouselFocalY()).isEqualByComparingTo("0.5");
        assertThat(artwork.getCarouselZoom()).isEqualByComparingTo("1");
    }
}
