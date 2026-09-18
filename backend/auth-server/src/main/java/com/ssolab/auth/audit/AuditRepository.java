package com.ssolab.auth.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditRepository extends JpaRepository<AuditEntity, UUID>,
    JpaSpecificationExecutor<AuditEntity> {
}
