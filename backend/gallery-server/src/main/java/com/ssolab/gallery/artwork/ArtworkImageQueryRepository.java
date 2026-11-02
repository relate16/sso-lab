package com.ssolab.gallery.artwork;

import java.util.Optional;

public interface ArtworkImageQueryRepository {
    Optional<ArtworkImageEntity> findPublishedByStorageKey(String storageKey);
    Optional<ArtworkImageEntity> findByStorageKey(String storageKey);
}
