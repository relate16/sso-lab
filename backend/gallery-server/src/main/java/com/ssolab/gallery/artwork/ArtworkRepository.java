package com.ssolab.gallery.artwork;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtworkRepository extends JpaRepository<ArtworkEntity, UUID> {
    Optional<ArtworkEntity> findByPublicIdAndPublishedTrue(UUID publicId);
    List<ArtworkEntity> findTop4ByPublishedTrueOrderByPublishedAtDesc();
}
