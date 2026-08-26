package com.ssolab.auth.admin.reauth;

import com.ssolab.auth.identity.model.UserIdentityEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity(name = "AdminReauthProof")
@Table(name = "admin_reauth_proofs", schema = "auth")
public class AdminReauthProofEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private UserIdentityEntity actor;

    @Column(name = "proof_hash", nullable = false, unique = true, columnDefinition = "bytea")
    private byte[] proofHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "authentication_method", nullable = false, length = 20)
    private AdminReauthMethod method;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AdminReauthProofEntity() {
    }

    public static AdminReauthProofEntity create(
        UserIdentityEntity actor,
        byte[] proofHash,
        AdminReauthMethod method,
        Instant issuedAt,
        Instant expiresAt
    ) {
        AdminReauthProofEntity proof = new AdminReauthProofEntity();
        proof.id = UUID.randomUUID();
        proof.actor = Objects.requireNonNull(actor);
        proof.proofHash = Objects.requireNonNull(proofHash).clone();
        proof.method = Objects.requireNonNull(method);
        proof.issuedAt = Objects.requireNonNull(issuedAt);
        proof.expiresAt = Objects.requireNonNull(expiresAt);
        return proof;
    }

    public boolean isValidAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public UUID getId() { return id; }
    public UserIdentityEntity getActor() { return actor; }
    public Instant getExpiresAt() { return expiresAt; }
    public AdminReauthMethod getMethod() { return method; }
}
