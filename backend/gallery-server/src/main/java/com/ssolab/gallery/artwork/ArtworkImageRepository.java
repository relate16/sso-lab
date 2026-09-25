package com.ssolab.gallery.artwork;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtworkImageRepository extends JpaRepository<ArtworkImageEntity, UUID> {
    Optional<ArtworkImageEntity> findByStorageKeyAndArtworkPublishedTrue(String storageKey);
    Optional<ArtworkImageEntity> findByStorageKey(String storageKey);
}
