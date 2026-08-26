package com.ssolab.auth.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity(name = "IdentityRole")
@Table(name = "roles", schema = "auth")
public class RoleEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 30)
    private RoleName name;

    protected RoleEntity() {
    }

    public UUID getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }
}
