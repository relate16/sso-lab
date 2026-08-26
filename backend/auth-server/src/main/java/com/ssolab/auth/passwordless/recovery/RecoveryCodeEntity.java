package com.ssolab.auth.passwordless.recovery;

import com.ssolab.auth.identity.model.UserIdentityEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity(name = "RecoveryCode")
@Table(name = "recovery_codes", schema = "auth")
public class RecoveryCodeEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserIdentityEntity user;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    protected RecoveryCodeEntity() {
    }

    public static RecoveryCodeEntity create(
        UUID id,
        UserIdentityEntity user,
        UUID batchId,
        String codeHash,
        Instant now
    ) {
        RecoveryCodeEntity code = new RecoveryCodeEntity();
        code.id = Objects.requireNonNull(id);
        code.user = Objects.requireNonNull(user);
        code.batchId = Objects.requireNonNull(batchId);
        code.codeHash = Objects.requireNonNull(codeHash);
        code.createdAt = Objects.requireNonNull(now);
        return code;
    }

    public void use(Instant now) {
        usedAt = Objects.requireNonNull(now);
    }

    public void invalidate(Instant now) {
        invalidatedAt = Objects.requireNonNull(now);
    }

    public UUID getId() {
        return id;
    }

    public UserIdentityEntity getUser() {
        return user;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }
}
