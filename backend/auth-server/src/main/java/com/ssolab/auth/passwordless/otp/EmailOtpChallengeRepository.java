package com.ssolab.auth.passwordless.otp;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EmailOtpChallengeRepository
    extends JpaRepository<EmailOtpChallengeEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailOtpChallengeEntity> findLockedById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailOtpChallengeEntity>
        findFirstByUser_IdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            UUID userId,
            OtpPurpose purpose
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<EmailOtpChallengeEntity> findByUser_IdAndPurposeAndConsumedAtIsNull(
        UUID userId,
        OtpPurpose purpose
    );
}
