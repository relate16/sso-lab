package com.ssolab.gallery.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssolab.gallery.api.GalleryDtos;
import com.ssolab.gallery.api.GalleryValidationException;
import com.ssolab.gallery.artwork.ArtworkEntity;
import com.ssolab.gallery.artwork.ArtworkRepository;
import com.ssolab.gallery.artwork.FrameType;
import com.ssolab.gallery.artwork.SaleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {
    @Mock InquiryRepository inquiries;
    @Mock ArtworkRepository artworks;

    @Test
    void validatesConsentAndPersistsPlainTextInquiryForPublishedArtwork() {
        var artwork = new ArtworkEntity("Winter", "Description", 2026, "Oil", new BigDecimal("40"),
            new BigDecimal("60"), null, SaleStatus.AVAILABLE, FrameType.NONE, false, 0, Instant.now());
        when(artworks.findByPublicIdAndPublishedTrue(artwork.getPublicId())).thenReturn(Optional.of(artwork));
        when(inquiries.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new InquiryService(inquiries, artworks);
        var request = new GalleryDtos.InquiryCreate(artwork.getPublicId(), " Visitor ",
            "visitor@example.test", " ", "Is this available?", true);

        assertThat(service.create(request)).isNotNull();
        var saved = ArgumentCaptor.forClass(InquiryEntity.class);
        verify(inquiries).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Visitor");
        assertThat(saved.getValue().getPhone()).isNull();
        assertThat(saved.getValue().getStatus()).isEqualTo(InquiryStatus.NEW);
    }

    @Test
    void rejectsInquiryWithoutPrivacyConsentBeforePersistence() {
        var service = new InquiryService(inquiries, artworks);
        var request = new GalleryDtos.InquiryCreate(java.util.UUID.randomUUID(), "Visitor",
            "visitor@example.test", null, "Question", false);
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(GalleryValidationException.class);
    }
}
