package com.lawfirm.erp.modules.casemanagement.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.modules.casemanagement.enums.BailStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CaseStage;
import com.lawfirm.erp.modules.casemanagement.enums.CaseStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CaseType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cases", indexes = {
        @Index(name = "idx_cases_firm_type", columnList = "firmId, caseType"),
        @Index(name = "idx_cases_firm_stage", columnList = "firmId, caseStage"),
        @Index(name = "idx_cases_assigned_to", columnList = "assignedTo"),
        @Index(name = "idx_cases_filing_date", columnList = "firmId, filingDate DESC"),
        @Index(name = "idx_cases_case_number", columnList = "firmId, caseNumber", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Case extends ActiveAuditableEntity {

    @Column(nullable = false)
    private UUID firmId;

    @Column(length = 30, nullable = false)
    private String caseNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private CaseType caseType;

    @Column(length = 200, nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private CaseStage caseStage;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private CaseStatus status;

    @Column(length = 100)
    private String courtName;

    @Column(name = "court_case_number", length = 50)
    private String courtCaseNumber;

    @Column(length = 100)
    private String judgeName;

    private LocalDate filingDate;

    @Column(length = 50)
    private String filingNumber;

    private UUID assignedTo;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "mediation_date")
    private LocalDate mediationDate;

    @Column(name = "mediation_outcome", columnDefinition = "TEXT")
    private String mediationOutcome;

    @Column(name = "written_statement_deadline")
    private LocalDate writtenStatementDeadline;

    @Column(length = 50)
    private String firNumber;

    @Column(name = "fir_date")
    private LocalDate firDate;

    @Column(length = 100)
    private String policeStation;

    @Column(length = 100)
    private String investigationAuthority;

    @Column(name = "arrest_date")
    private LocalDate arrestDate;

    @Column(name = "charge_sheet_date")
    private LocalDate chargeSheetDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private BailStatus bailStatus;
}
