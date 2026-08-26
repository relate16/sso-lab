package com.ssolab.auth.passwordless.recovery;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCodeEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select code from RecoveryCode code where code.user.id = :userId")
    List<RecoveryCodeEntity> findAllForUpdateByUserId(@Param("userId") UUID userId);
}
