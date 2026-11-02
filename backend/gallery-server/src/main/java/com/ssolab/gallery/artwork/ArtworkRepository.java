package com.ssolab.gallery.artwork;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtworkRepository extends JpaRepository<ArtworkEntity, UUID>, ArtworkQueryRepository { }
