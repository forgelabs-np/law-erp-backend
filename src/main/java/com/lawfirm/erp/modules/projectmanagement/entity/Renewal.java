package com.lawfirm.erp.modules.projectmanagement.entity;

import com.lawfirm.erp.modules.projectmanagement.enums.RenewalRecurrence;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_renewals", indexes = {
        @Index(name = "idx_renew_project", columnList = "projectId"),
        @Index(name = "idx_renew_assigned", columnList = "assignedToId")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Renewal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID projectId;

    @Column(name = "renewal_type_id", nullable = false)
    private Long renewalTypeId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private RenewalRecurrence recurrence = RenewalRecurrence.ONE_TIME;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** NULL = ongoing (generate instances indefinitely for recurring). */
    private LocalDate endDate;

    /** Who handles this renewal — nullable (unassigned). */
    private UUID assignedToId;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private RenewalStatus status = RenewalStatus.ACTIVE;

    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;
}
