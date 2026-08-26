package com.ssolab.auth.passwordless.otp;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface PendingRegistrationRepository
    extends JpaRepository<PendingRegistrationEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PendingRegistrationEntity> findLockedById(UUID id);
}
