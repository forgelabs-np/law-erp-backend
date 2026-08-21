package com.lawfirm.erp.modules.projectmanagement.entity;

import com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_renewal_instances", indexes = {
        @Index(name = "idx_ri_renewal", columnList = "renewalId"),
        @Index(name = "idx_ri_due_status", columnList = "dueDate, status")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class RenewalInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "renewal_id", nullable = false)
    private Long renewalId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private RenewalInstanceStatus status = RenewalInstanceStatus.PENDING;

    private LocalDateTime completedAt;

    private UUID completedById;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
