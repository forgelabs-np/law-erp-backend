package com.lawfirm.erp.rbac.entity;

import com.lawfirm.erp.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks one async template-permission sync (spec §3, §6). One row per
 * PUT .../templates/{id}/permissions call that produces a fan-out.
 *
 * Correlation anchor: every audit row this job writes carries the job id,
 * so "why did Firm X's PARALEGAL change" traces back to the SA action.
 */
@Entity
@Table(name = "sync_jobs")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyncJob extends AuditableEntity {

    /** Template whose edit triggered the fan-out. */
    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    /** Denormalized for job listings without a role join. */
    @Column(name = "template_code", nullable = false)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncStatus status;

    private int firmsTotal;
    private int firmsCompleted;
    private int firmsFailed;

    @Column(length = 4000)
    private String errorSummary;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public enum SyncStatus { PENDING, RUNNING, COMPLETED, COMPLETED_WITH_FAILURES, FAILED }
}
