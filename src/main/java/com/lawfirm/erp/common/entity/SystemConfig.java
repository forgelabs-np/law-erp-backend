package com.lawfirm.erp.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/** DB-backed key-value store. GLOBAL or FIRM scoped. Sensitive values AES-256 encrypted. */
@Entity
@Table(
        name = "system_config",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"scope", "firm_id", "config_key"},
                        name = "uq_system_config_scope_firm_key")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SystemConfig {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** GLOBAL or FIRM. GLOBAL values have firmId = null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 10)
    private ConfigScope scope;

    /** Null for GLOBAL scope. Set to firm's UUID for FIRM scope. */
    @Column(name = "firm_id")
    private UUID firmId;

    /** Config key, e.g. SMTP_HOST or BRAND_COLOR_PRIMARY. */
    @Column(name = "config_key", nullable = false, length = 50)
    private String configKey;

    /** Plaintext or AES-256 encrypted depending on encrypted flag. */
    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    /** If true, configValue is AES-256 encrypted. */
    @Builder.Default
    @Column(name = "encrypted", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean encrypted = false;

    @Column(length = 200)
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ConfigScope {
        GLOBAL,
        FIRM
    }
}
