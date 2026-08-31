package com.ssolab.auth.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditRepository repository;
    private final Clock clock;

    public AuditService(AuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
        AuditEvent event,
        UUID actorId,
        UUID targetId,
        boolean success,
        AuditSource source,
        String traceId
    ) {
        repository.save(AuditEntity.create(
            event, actorId, targetId, success, source, traceId, clock.instant()
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordWithinTransaction(
        AuditEvent event,
        UUID actorId,
        UUID targetId,
        boolean success,
        AuditSource source,
        String traceId
    ) {
        repository.save(AuditEntity.create(
            event, actorId, targetId, success, source, traceId, clock.instant()
        ));
    }

    @Transactional(readOnly = true)
    public Page<AuditEntity> findRecent(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return repository.findAll(PageRequest.of(
            safePage, safeSize, Sort.by(Sort.Direction.DESC, "occurredAt")
        ));
    }
}
