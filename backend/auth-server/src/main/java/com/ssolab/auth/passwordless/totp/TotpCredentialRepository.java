package com.ssolab.auth.passwordless.totp;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface TotpCredentialRepository extends JpaRepository<TotpCredentialEntity, UUID> {

    boolean existsByUserIdAndStatus(UUID userId, TotpCredentialStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TotpCredentialEntity> findLockedByUserId(UUID userId);
}
