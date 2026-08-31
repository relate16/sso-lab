package com.ssolab.auth.passwordless.emailchange;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PendingEmailChangeRepository
    extends JpaRepository<PendingEmailChangeEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pending from PendingEmailChange pending "
        + "where pending.user.id = :userId and pending.consumedAt is null "
        + "and pending.invalidatedAt is null")
    List<PendingEmailChangeEntity> findActiveLockedByUserId(@Param("userId") UUID userId);
}
