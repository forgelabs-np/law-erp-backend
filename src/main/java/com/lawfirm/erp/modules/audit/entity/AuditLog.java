package com.lawfirm.erp.modules.audit.entity;

import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight audit log — ~150 bytes per row.
 *
 * Key decisions:
 *  - action     CHAR(30)    fixed-width, enum-backed, fast equality scans
 *  - entity_type CHAR(20)   fixed-width, enum-backed
 *  - user_type  CHAR(1)     single char: 'S'=SUPER_ADMIN 'F'=FIRM_USER 'C'=CLIENT
 *  - summary    VARCHAR(200) human-readable line — replaces jsonb payload entirely
 *  - ip_address VARCHAR(45) IPv6 max = 39 chars
 *
 * Indexes:
 *   - (firm_id, created_at DESC) — firm admin timeline, most common query
 *   - (firm_id, user_id, created_at DESC) — "what did advocate X do?"
 *   - (action, created_at DESC) — super admin action filter
 */
@Entity
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_firm_created", columnList = "firm_id, created_at DESC"),
                @Index(name = "idx_audit_firm_user_created", columnList = "firm_id, user_id, created_at DESC"),
                @Index(name = "idx_audit_action_created", columnList = "action, created_at DESC")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuditLog {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "firm_id")
    private UUID firmId;                    // null only for SUPER_ADMIN actions

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_type", nullable = false, length = 15)
    private String userType;                // 'S'=SUPER_ADMIN 'A'=FIRM 'F'=FIRM_USER 'C'=CLIENT

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "CHAR(30)")
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 20, columnDefinition = "CHAR(20)")
    private AuditEntity entityType;

    @Column(name = "entity_id")
    private UUID entityId;                  // UUID of the affected row

    // ── Context ──────────────────────────────────────────────────────────
    /**
     * Human-readable one-liner for the timeline UI.
     * Examples:
     *   "Created employee john.doe@firm.com (ADVOCATE)"
     *   "Changed case #142 status: OPEN → CLOSED"
     *   "Uploaded document: contract_v2.pdf"
     * Keep it under 200 chars. No JSON, no object dumps.
     */
    @Column(length = 200)
    private String summary;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;               // IPv6 max = 39 chars

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // ── Convenience factory ───────────────────────────────────────────────
    public static AuditLog of(UUID firmId, UUID userId, String userTypeChar,
                              AuditAction action, AuditEntity entityType,
                              UUID entityId, String summary, String ipAddress) {
        return AuditLog.builder()
                .firmId(firmId)
                .userId(userId)
                .userType(userTypeChar)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .summary(summary)
                .ipAddress(ipAddress)
                .build();
    }
}