package com.lawfirm.erp.modules.casemanagement.entity;

import com.lawfirm.erp.entity.base.AuditableEntity;
import com.lawfirm.erp.modules.casemanagement.enums.TimelineEventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "case_timeline", indexes = {
        @Index(name = "idx_timeline_case", columnList = "caseId, createdAt DESC"),
        @Index(name = "idx_timeline_type", columnList = "caseId, eventType")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CaseTimelineEvent extends AuditableEntity {

    @Column(nullable = false)
    private UUID firmId;

    @Column(nullable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(length = 25, nullable = false)
    private TimelineEventType eventType;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;
}
