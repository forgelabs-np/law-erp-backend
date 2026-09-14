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

    /** Config key, e.g. SMTP_HOST or MFA_ENABLED. */
    @Column(name = "config_key", nullable = false, length = 50)
    private String configKey;

    /** Plaintext or AES-256 encrypted depending on encrypted flag. */
    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    /** If true, configValue is AES-256 encrypted. */
    @Builder.Default
    @Column(name = "encrypted", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean encrypted = false;

    /** SETTINGS submodule grouping, e.g. APP, SECURITY, EMAIL, BRAND. */
    @Column(name = "config_group", length = 50)
    private String configGroup;

    /** UI control + server-side validation type: TEXT, NUMBER, RADIO, DROPDOWN, PASSWORD, URL. */
    @Builder.Default
    @Column(name = "input_type", nullable = false, length = 20)
    private String inputType = "TEXT";

    /** Comma-separated options for RADIO/DROPDOWN, e.g. Y,N. Null otherwise. */
    @Column(name = "allowed_values", columnDefinition = "TEXT")
    private String allowedValues;

    /** Inactive rows are ignored by reads and hidden from the admin settings list. */
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /** If false, the value is locked after its first (non-empty) set and cannot be deleted via API. */
    @Builder.Default
    @Column(name = "allow_edit", nullable = false)
    private Boolean allowEdit = true;

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

    /** True when the stored value is AES-256 encrypted. */
    public boolean isSensitive() {
        return "PASSWORD".equals(inputType);
    }

    /** Null-safe active check (older rows default to active). */
    public boolean isActive() {
        return !Boolean.FALSE.equals(active);
    }

    /** Null-safe allowEdit check. */
    public boolean isAllowEdit() {
        return !Boolean.FALSE.equals(allowEdit);
    }

    public enum ConfigScope {
        GLOBAL,
        FIRM
    }
}
