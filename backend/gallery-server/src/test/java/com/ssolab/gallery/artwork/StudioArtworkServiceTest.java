package com.ssolab.gallery.artwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssolab.gallery.api.GalleryDtos;
import com.ssolab.gallery.api.GalleryValidationException;
import com.ssolab.gallery.storage.ArtworkImageStorage;
import com.ssolab.gallery.storage.StoredArtworkImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class StudioArtworkServiceTest {
    @Mock ArtworkRepository artworks;
    @Mock ArtworkImageRepository images;
    @Mock ArtworkImageStorage storage;

    @Test
    void draftMayBeCreatedWithoutImageButCannotBePublished() {
        var service = service();
        when(artworks.count()).thenReturn(0L);
        when(artworks.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        GalleryDtos.Artwork created = service.create(upsert(false));
        assertThat(created.published()).isFalse();

        ArtworkEntity existing = artwork("Draft", 0);
        when(artworks.findByPublicId(existing.getPublicId())).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.update(existing.getPublicId(), upsert(true)))
            .isInstanceOf(GalleryValidationException.class);
    }

    @Test
    void bulkReorderUpdatesAllRequestedArtworksInOneTransaction() {
        ArtworkEntity first = artwork("First", 0);
        ArtworkEntity second = artwork("Second", 1);
        when(artworks.findAll()).thenReturn(List.of(first, second));
        service().reorder(new GalleryDtos.ReorderRequest(List.of(
            new GalleryDtos.ReorderItem(first.getPublicId(), 1),
            new GalleryDtos.ReorderItem(second.getPublicId(), 0))));
        assertThat(first.getDisplayOrder()).isEqualTo(1);
        assertThat(second.getDisplayOrder()).isZero();
    }

    @Test
    void uploadPersistsImageThroughTheArtworkAggregateOnly() throws IOException {
        ArtworkEntity existing = artwork("Draft", 0);
        var stored = new StoredArtworkImage("12345678-1234-1234-1234-123456789abc",
            "original.jpg", "web.jpg", "thumbnail.jpg", "image/jpeg", 1200, 800);
        var file = new MockMultipartFile("file", "winter.jpg", "image/jpeg", new byte[] { 1 });
        when(artworks.findByPublicId(existing.getPublicId())).thenReturn(Optional.of(existing));
        when(storage.store(file)).thenReturn(stored);

        GalleryDtos.Image uploaded = service().upload(existing.getPublicId(), file);

        assertThat(uploaded.primary()).isTrue();
        assertThat(uploaded.originalUrl()).endsWith("/media/12345678-1234-1234-1234-123456789abc/original");
        assertThat(existing.getImages()).hasSize(1);
        verify(artworks).flush();
        verify(images, never()).save(any());
    }

    private StudioArtworkService service() {
        return new StudioArtworkService(artworks, images, storage,
            Clock.fixed(Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC));
    }

    private ArtworkEntity artwork(String title, int order) {
        return new ArtworkEntity(title, "Description", 2026, "Oil", new BigDecimal("40"),
            new BigDecimal("60"), null, SaleStatus.AVAILABLE, FrameType.FLOATING_FRAME, false,
            order, Instant.parse("2026-09-20T00:00:00Z"));
    }

    private GalleryDtos.ArtworkUpsert upsert(boolean published) {
        return new GalleryDtos.ArtworkUpsert("Winter", "Description", 2026, "Oil",
            new BigDecimal("40"), new BigDecimal("60"), null, SaleStatus.AVAILABLE,
            FrameType.FLOATING_FRAME, published, false);
    }
}
