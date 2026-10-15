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

    private UUID parentCourtCaseId;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private RelationType relationType;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private CourtLevel courtLevel;

    @Column(length = 100, nullable = false)
    private String courtName;

    @Column(length = 50)
    private String courtCaseNumber;

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

    private UUID advocateId;

    @Column(length = 100)
    private String judgeName;

    private LocalDate judgmentDate;

    @Column(columnDefinition = "TEXT")
    private String judgmentSummary;

    private UUID decisionInFavorOfPartyId;

    private LocalDate appealDeadline;

    @Column(name = "appeal_requires_leave")
    private Boolean appealRequiresLeave;

    @Column(name = "party_is_state")
    private boolean partyIsState;

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
