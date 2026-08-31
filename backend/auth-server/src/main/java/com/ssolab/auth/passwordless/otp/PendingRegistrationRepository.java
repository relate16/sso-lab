package com.ssolab.auth.passwordless.otp;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PendingRegistrationRepository
    extends JpaRepository<PendingRegistrationEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PendingRegistrationEntity> findLockedById(UUID id);

    @Modifying
    @Query(value = "DELETE FROM auth.pending_registrations "
        + "WHERE normalized_user_id = :userId OR email_lookup_hash = :emailHash",
        nativeQuery = true)
    int deletePersonalData(
        @Param("userId") String normalizedUserId,
        @Param("emailHash") byte[] emailLookupHash
    );
}
