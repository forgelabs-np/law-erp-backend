package com.lawfirm.erp.modules.casemanagement.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventType;
import com.lawfirm.erp.modules.casemanagement.enums.NextEventType;
import com.lawfirm.erp.modules.casemanagement.enums.OutcomeType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "court_events", indexes = {
        @Index(name = "idx_ce_court_case", columnList = "courtCaseId"),
        @Index(name = "idx_ce_date", columnList = "firmId, scheduledDate DESC"),
        @Index(name = "idx_ce_advocate_date", columnList = "attendingAdvocateId, scheduledDate DESC"),
        @Index(name = "idx_ce_status_date", columnList = "firmId, status, scheduledDate DESC")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CourtEvent extends ActiveAuditableEntity {

    @Column(nullable = false)
    private UUID firmId;

    @Column(nullable = false)
    private UUID courtCaseId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private CourtEventType eventType;

    @Column(nullable = false)
    private int sequenceNo;

    @Column(nullable = false)
    private LocalDate scheduledDate;

    private LocalTime scheduledTime;

    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(length = 12, nullable = false)
    private CourtEventStatus status = CourtEventStatus.SCHEDULED;

    @Column(columnDefinition = "TEXT")
    private String outcome;

    @Enumerated(EnumType.STRING)
    @Column(length = 25)
    private OutcomeType outcomeType;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private NextEventType nextEventType;

    private UUID nextEventId;

    private UUID attendingAdvocateId;

    @Column(length = 100)
    private String judgeName;

    @Column(length = 50)
    private String courtRoom;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
