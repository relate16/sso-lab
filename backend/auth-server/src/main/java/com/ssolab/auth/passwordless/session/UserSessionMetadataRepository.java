package com.ssolab.auth.passwordless.session;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface UserSessionMetadataRepository
    extends JpaRepository<UserSessionMetadataEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserSessionMetadataEntity> findLockedBySessionId(String sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserSessionMetadataEntity> findLockedByPublicIdAndUser_Id(UUID publicId, UUID userId);

    List<UserSessionMetadataEntity> findByUser_IdAndInvalidatedAtIsNull(UUID userId);
}
