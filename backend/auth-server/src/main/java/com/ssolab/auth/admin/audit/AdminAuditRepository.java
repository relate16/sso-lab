package com.ssolab.auth.admin.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditRepository extends JpaRepository<AdminAuditEntity, UUID> {
}
