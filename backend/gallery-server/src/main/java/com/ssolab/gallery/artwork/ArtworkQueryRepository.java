package com.ssolab.gallery.artwork;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ArtworkQueryRepository {
    List<ArtworkEntity> findLatestPublished(int limit);
    Optional<ArtworkEntity> findPublishedByPublicId(UUID publicId);
    Optional<ArtworkEntity> findByPublicId(UUID publicId);
    List<ArtworkEntity> findAllForStudio();
    List<ArtworkEntity> findAllByPublicIds(Collection<UUID> publicIds);
    Page<ArtworkEntity> search(ArtworkSearchCriteria criteria, Pageable pageable);
}
