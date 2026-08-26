package com.ssolab.auth.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity(name = "SystemConfig")
@Table(name = "system_config", schema = "auth")
public class SystemConfigEntity {

    @Id
    @Column(name = "config_key", length = 100)
    private String key;

    @Column(name = "config_value", nullable = false, length = 200)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SystemConfigEntity() {
    }

    public static SystemConfigEntity create(String key, String value, Instant now) {
        SystemConfigEntity config = new SystemConfigEntity();
        config.key = Objects.requireNonNull(key);
        config.value = Objects.requireNonNull(value);
        config.updatedAt = Objects.requireNonNull(now);
        return config;
    }

    public void update(String value, Instant now) {
        this.value = Objects.requireNonNull(value);
        this.updatedAt = Objects.requireNonNull(now);
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
