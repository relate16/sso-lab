package com.ssolab.auth.identity.model;

import com.ssolab.auth.identity.crypto.EncryptedEmail;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity(name = "UserIdentity")
@Table(name = "users", schema = "auth")
public class UserIdentityEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 30)
    private String userId;

    @Column(name = "normalized_user_id", nullable = false, unique = true, length = 30)
    private String normalizedUserId;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "email_ciphertext", nullable = false, columnDefinition = "bytea")
    private byte[] emailCiphertext;

    @Column(name = "email_iv", nullable = false, columnDefinition = "bytea")
    private byte[] emailIv;

    @Column(name = "email_key_version", nullable = false)
    private int emailKeyVersion;

    @Column(name = "email_lookup_hash", nullable = false, unique = true, columnDefinition = "bytea")
    private byte[] emailLookupHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        schema = "auth",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<RoleEntity> roles = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_groups",
        schema = "auth",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "group_id")
    )
    private Set<IdentityGroupEntity> groups = new HashSet<>();

    protected UserIdentityEntity() {
    }

    public static UserIdentityEntity create(
        UUID id,
        String normalizedUserId,
        String username,
        EncryptedEmail encryptedEmail,
        byte[] emailLookupHash,
        Instant now
    ) {
        UserIdentityEntity user = new UserIdentityEntity();
        user.id = Objects.requireNonNull(id);
        user.userId = Objects.requireNonNull(normalizedUserId);
        user.normalizedUserId = normalizedUserId;
        user.username = Objects.requireNonNull(username);
        user.emailCiphertext = encryptedEmail.ciphertext();
        user.emailIv = encryptedEmail.iv();
        user.emailKeyVersion = encryptedEmail.keyVersion();
        user.emailLookupHash = emailLookupHash.clone();
        user.status = AccountStatus.ACTIVE;
        user.createdAt = Objects.requireNonNull(now);
        user.updatedAt = now;
        return user;
    }

    public void addRole(RoleEntity role) {
        roles.add(Objects.requireNonNull(role));
    }

    public void removeRole(RoleEntity role) {
        roles.remove(role);
    }

    public boolean hasRole(RoleName roleName) {
        return roles.stream().anyMatch(role -> role.getName() == roleName);
    }

    public void addGroup(IdentityGroupEntity group) {
        groups.add(Objects.requireNonNull(group));
    }

    public void removeGroup(IdentityGroupEntity group) {
        groups.remove(group);
    }

    public void suspend(Instant now) {
        status = AccountStatus.SUSPENDED;
        updatedAt = Objects.requireNonNull(now);
    }

    public void activate(Instant now) {
        status = AccountStatus.ACTIVE;
        updatedAt = Objects.requireNonNull(now);
    }

    public void changeUsername(String username, Instant now) {
        this.username = Objects.requireNonNull(username);
        updatedAt = Objects.requireNonNull(now);
    }

    public void changeEmail(
        EncryptedEmail encryptedEmail,
        byte[] emailLookupHash,
        Instant now
    ) {
        Objects.requireNonNull(encryptedEmail);
        emailCiphertext = encryptedEmail.ciphertext();
        emailIv = encryptedEmail.iv();
        emailKeyVersion = encryptedEmail.keyVersion();
        this.emailLookupHash = Objects.requireNonNull(emailLookupHash).clone();
        updatedAt = Objects.requireNonNull(now);
    }

    public EncryptedEmail encryptedEmail() {
        return new EncryptedEmail(emailCiphertext, emailIv, emailKeyVersion);
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getNormalizedUserId() {
        return normalizedUserId;
    }

    public String getUsername() {
        return username;
    }

    public byte[] getEmailLookupHash() {
        return emailLookupHash.clone();
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<RoleEntity> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public Set<IdentityGroupEntity> getGroups() {
        return Collections.unmodifiableSet(groups);
    }
}
