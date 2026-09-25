package com.ssolab.gallery.artwork;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ArtworkRepository extends JpaRepository<ArtworkEntity, UUID>, JpaSpecificationExecutor<ArtworkEntity> {
    Optional<ArtworkEntity> findByPublicIdAndPublishedTrue(UUID publicId);
    Optional<ArtworkEntity> findByPublicId(UUID publicId);
    List<ArtworkEntity> findTop4ByPublishedTrueOrderByPublishedAtDesc();
}
