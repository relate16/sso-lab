package com.ssolab.auth.admin.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditService {

    private final AdminAuditRepository repository;
    private final Clock clock;

    public AdminAuditService(AdminAuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
        AdminAuditEvent event,
        UUID actorId,
        UUID targetId,
        boolean success,
        AdminAuditSource source,
        String traceId
    ) {
        repository.save(AdminAuditEntity.create(
            event, actorId, targetId, success, source, traceId, clock.instant()
        ));
    }

    @Transactional(readOnly = true)
    public Page<AdminAuditEntity> findRecent(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return repository.findAll(PageRequest.of(
            safePage, safeSize, Sort.by(Sort.Direction.DESC, "occurredAt")
        ));
    }
}
