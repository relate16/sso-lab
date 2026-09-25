package com.ssolab.gallery.artwork;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssolab.gallery.api.GalleryNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicArtworkServiceTest {
    @Test
    void unpublishedArtworkCannotBeResolvedByPublicId() {
        ArtworkRepository repository = mock(ArtworkRepository.class);
        UUID publicId = UUID.randomUUID();
        when(repository.findByPublicIdAndPublishedTrue(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new PublicArtworkService(repository).detail(publicId))
            .isInstanceOf(GalleryNotFoundException.class);
    }
}
