package com.lawfirm.erp.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "firm_configs",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"firm_id", "config_key"},
                        name = "uq_firm_configs_firm_key")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FirmConfig {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "firm_id", nullable = false)
    private UUID firmId;

    @Column(name = "config_key", nullable = false, length = 50)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Builder.Default
    @Column(name = "encrypted", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean encrypted = false;

    @Column(name = "config_group", length = 50)
    private String configGroup;

    @Builder.Default
    @Column(name = "input_type", nullable = false, length = 20)
    private String inputType = "TEXT";

    @Column(name = "allowed_values", columnDefinition = "TEXT")
    private String allowedValues;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

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

    public boolean isActive() {
        return !Boolean.FALSE.equals(active);
    }

    public boolean isAllowEdit() {
        return !Boolean.FALSE.equals(allowEdit);
    }
}
