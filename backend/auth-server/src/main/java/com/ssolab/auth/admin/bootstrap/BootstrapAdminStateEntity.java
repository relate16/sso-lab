package com.ssolab.auth.admin.bootstrap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity(name = "BootstrapAdminState")
@Table(name = "bootstrap_admin_state", schema = "auth")
public class BootstrapAdminStateEntity {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "singleton_id")
    private short id;

    @Column(name = "email_lookup_hash", nullable = false, columnDefinition = "bytea")
    private byte[] emailLookupHash;

    @Column(name = "claimed_by")
    private UUID claimedBy;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BootstrapAdminStateEntity() {
    }

    public static BootstrapAdminStateEntity pending(byte[] emailLookupHash, Instant now) {
        BootstrapAdminStateEntity state = new BootstrapAdminStateEntity();
        state.id = SINGLETON_ID;
        state.emailLookupHash = Objects.requireNonNull(emailLookupHash).clone();
        state.createdAt = Objects.requireNonNull(now);
        state.updatedAt = now;
        return state;
    }

    public void claim(UUID userId, Instant now) {
        if (claimedAt != null) {
            throw new IllegalStateException("bootstrap admin has already been claimed");
        }
        claimedBy = Objects.requireNonNull(userId);
        claimedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public void unlinkDeletedClaim(byte[] tombstoneHash, Instant now) {
        if (!isClaimed()) {
            throw new IllegalStateException("unclaimed bootstrap state cannot be unlinked");
        }
        emailLookupHash = Objects.requireNonNull(tombstoneHash).clone();
        updatedAt = Objects.requireNonNull(now);
    }

    public byte[] getEmailLookupHash() { return emailLookupHash.clone(); }
    public UUID getClaimedBy() { return claimedBy; }
    public Instant getClaimedAt() { return claimedAt; }
    public boolean isClaimed() { return claimedAt != null; }
}
