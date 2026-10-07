package com.lawfirm.erp.modules.audit.entity;

import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

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
    private UUID firmId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_type", nullable = false, length = 15)
    private String userType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "CHAR(30)")
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 20, columnDefinition = "CHAR(20)")
    private AuditEntity entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(length = 200)
    private String summary;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

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