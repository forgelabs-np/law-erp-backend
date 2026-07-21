package com.lawfirm.erp.firm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/** Per-firm SMTP email config. Password stored AES-256 encrypted, never returned in API. */
@Entity
@Table(name = "firm_email_configs")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FirmEmailConfig {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** One config per firm. FK to firms table. */
    @Column(name = "firm_id", nullable = false, unique = true)
    private UUID firmId;

    @Column(name = "smtp_host", nullable = false, length = 255)
    private String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    private Integer smtpPort;

    @Column(name = "smtp_username", nullable = false, length = 255)
    private String smtpUsername;

    /** AES-256 encrypted. API returns smtpPasswordSet bool instead. */
    @Column(name = "smtp_password", nullable = false, length = 512)
    private String smtpPassword;

    @Column(name = "from_name", nullable = false, length = 255)
    private String fromName;

    @Column(name = "from_address", nullable = false, length = 255)
    private String fromAddress;

    @Builder.Default
    @Column(name = "use_tls", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean useTls = true;

    @Builder.Default
    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean isActive = true;

    @Column(name = "tested_at")
    private LocalDateTime testedAt;

    @Column(name = "test_passed", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean testPassed;

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
}
