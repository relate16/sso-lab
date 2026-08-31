package com.ssolab.auth.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity(name = "Audit")
@Table(name = "audit_logs", schema = "auth")
public class AuditEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuditEvent event;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(nullable = false)
    private boolean success;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditSource source;

    @Column(name = "trace_id", length = 100)
    private String traceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEntity() {
    }

    public static AuditEntity create(
        AuditEvent event,
        UUID actorId,
        UUID targetId,
        boolean success,
        AuditSource source,
        String traceId,
        Instant occurredAt
    ) {
        AuditEntity audit = new AuditEntity();
        audit.id = UUID.randomUUID();
        audit.event = Objects.requireNonNull(event);
        audit.actorId = actorId;
        audit.targetId = targetId;
        audit.success = success;
        audit.source = Objects.requireNonNull(source);
        audit.traceId = traceId == null || traceId.isBlank() ? null : traceId;
        audit.occurredAt = Objects.requireNonNull(occurredAt);
        return audit;
    }

    public UUID getId() { return id; }
    public AuditEvent getEvent() { return event; }
    public UUID getActorId() { return actorId; }
    public UUID getTargetId() { return targetId; }
    public boolean isSuccess() { return success; }
    public AuditSource getSource() { return source; }
    public String getTraceId() { return traceId; }
    public Instant getOccurredAt() { return occurredAt; }
}
