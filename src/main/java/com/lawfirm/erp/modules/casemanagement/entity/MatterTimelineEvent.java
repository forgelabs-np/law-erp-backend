package com.lawfirm.erp.modules.casemanagement.entity;

import com.lawfirm.erp.entity.base.AuditableEntity;
import com.lawfirm.erp.modules.casemanagement.enums.TimelineEventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Immutable event log for a matter. Every entry carries an optional courtCaseId tag
 * so the aggregated multi-year feed can show which court each event belongs to.
 */
@Entity
@Table(name = "matter_timeline", indexes = {
        @Index(name = "idx_mt_matter", columnList = "matterId, createdAt DESC"),
        @Index(name = "idx_mt_court_case", columnList = "courtCaseId"),
        @Index(name = "idx_mt_type", columnList = "matterId, eventType")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MatterTimelineEvent extends AuditableEntity {

    @Column(nullable = false)
    private UUID firmId;

    @Column(nullable = false)
    private UUID matterId;

    /** Null for matter-level events (e.g. MATTER_CREATED). */
    private UUID courtCaseId;

    @Enumerated(EnumType.STRING)
    @Column(length = 25, nullable = false)
    private TimelineEventType eventType;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;
}
