package com.lawfirm.erp.modules.casemanagement.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.modules.casemanagement.enums.*;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per court instance the matter has actually been registered in.
 * A dispute that goes District → High → Supreme (and possibly remands back down)
 * is a chain of CourtCase rows, each with its own official number, stage,
 * advocate assignment and event stream.
 */
@Entity
@Table(name = "court_cases", indexes = {
        @Index(name = "idx_cc_matter", columnList = "matterId"),
        @Index(name = "idx_cc_firm_level", columnList = "firmId, courtLevel"),
        @Index(name = "idx_cc_firm_stage", columnList = "firmId, stage"),
        @Index(name = "idx_cc_parent", columnList = "parentCourtCaseId"),
        @Index(name = "idx_cc_ref", columnList = "firmId, ourCourtCaseRef", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CourtCase extends ActiveAuditableEntity {

    @Column(nullable = false)
    private UUID firmId;

    @Column(nullable = false)
    private UUID matterId;

    /** Null only for the very first CourtCase (or a writ filed directly at HC/SC). */
    private UUID parentCourtCaseId;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private RelationType relationType;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private CourtLevel courtLevel;

    @Column(length = 100, nullable = false)
    private String courtName;

    /** The court's own official registration number — entered manually, may be null until registry assigns it. */
    @Column(length = 50)
    private String courtCaseNumber;

    /** Our internal, always-populated reference: {matterNumber}-{levelCode}{n}. */
    @Column(length = 50, nullable = false)
    private String ourCourtCaseRef;

    @Column(nullable = false)
    private LocalDate filingDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 25, nullable = false)
    private CourtCaseStage stage;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private CourtCaseStatus status = CourtCaseStatus.ACTIVE;

    /** Who's handling this instance — often different at HC/SC (senior counsel briefed for appeal). */
    private UUID advocateId;

    @Column(length = 100)
    private String judgeName;

    private LocalDate judgmentDate;

    @Column(columnDefinition = "TEXT")
    private String judgmentSummary;

    private UUID decisionInFavorOfPartyId;

    private LocalDate appealDeadline;

    /**
     * True when this judgment can only be appealed by leave petition (High Court →
     * Supreme Court): the appeal is not automatic and the process differs.
     * Wrapper type (not primitive): the column was added after court_cases rows may
     * already exist, and a primitive would break hydration on legacy NULL values.
     */
    @Column(name = "appeal_requires_leave")
    private Boolean appealRequiresLeave;

    /** True when a government/state party is involved (affects statutory appeal windows). */
    @Column(name = "party_is_state")
    private boolean partyIsState;

    /**
     * Set by the scheduled deadline watcher once the appeal window lapses with no
     * appeal filed and no child CourtCase. Marks the judgment final; cleared when
     * the firm moves the case into EXECUTION.
     */
    @Column(name = "appeal_lapsed")
    private boolean appealLapsed;

    @Column(length = 50)
    private String firNumber;

    private LocalDate firDate;

    @Column(length = 100)
    private String policeStation;

    @Column(length = 100)
    private String investigationAuthority;

    private LocalDate arrestDate;

    private LocalDate chargeSheetDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private BailStatus bailStatus;

    private LocalDate mediationDate;

    @Column(columnDefinition = "TEXT")
    private String mediationOutcome;

    private LocalDate writtenStatementDeadline;
}
