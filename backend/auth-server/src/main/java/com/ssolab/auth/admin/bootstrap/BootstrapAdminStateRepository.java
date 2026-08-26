package com.ssolab.auth.admin.bootstrap;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BootstrapAdminStateRepository
    extends JpaRepository<BootstrapAdminStateEntity, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from BootstrapAdminState state where state.id = 1")
    Optional<BootstrapAdminStateEntity> findLocked();
}
