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

/**
 * Unified event record covering both Tarik (administrative date) and Peshi (actual hearing).
 * The Tarik/Peshi loop is a chained stream: when an event is marked HELD, the form records
 * what the court gave next and creates the next CourtEvent row — the digital diary entry.
 */
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

    /** Ordering within the CourtCase (1, 2, 3, ...). */
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

    /** What got scheduled next: TARIK, PESHI, JUDGMENT, or NONE (closed at this event). */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private NextEventType nextEventType;

    /** Forward pointer once the next event is created — renders the loop as a chain. */
    private UUID nextEventId;

    private UUID attendingAdvocateId;

    @Column(length = 100)
    private String judgeName;

    @Column(length = 50)
    private String courtRoom;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
