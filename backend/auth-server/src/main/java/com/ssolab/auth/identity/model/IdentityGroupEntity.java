package com.ssolab.auth.identity.model;

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

@Entity(name = "IdentityGroup")
@Table(name = "groups", schema = "auth")
public class IdentityGroupEntity {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private IdentityGroupEntity parent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IdentityGroupEntity() {
    }

    public static IdentityGroupEntity create(
        UUID id,
        String name,
        String normalizedName,
        IdentityGroupEntity parent,
        Instant now
    ) {
        IdentityGroupEntity group = new IdentityGroupEntity();
        group.id = Objects.requireNonNull(id);
        group.name = Objects.requireNonNull(name);
        group.normalizedName = Objects.requireNonNull(normalizedName);
        group.parent = parent;
        group.createdAt = Objects.requireNonNull(now);
        group.updatedAt = now;
        return group;
    }

    public void moveTo(IdentityGroupEntity newParent, Instant now) {
        this.parent = newParent;
        this.updatedAt = Objects.requireNonNull(now);
    }

    public void rename(String name, String normalizedName, Instant now) {
        this.name = Objects.requireNonNull(name);
        this.normalizedName = Objects.requireNonNull(normalizedName);
        this.updatedAt = Objects.requireNonNull(now);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public IdentityGroupEntity getParent() {
        return parent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
