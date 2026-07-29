package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.BailStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CaseStatus;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateCaseRequest {

    private String title;
    private String courtName;
    private String courtCaseNumber;
    private LocalDate filingDate;
    private String filingNumber;
    private UUID assignedTo;
    private String description;

    // Civil-specific
    private LocalDate mediationDate;
    private String mediationOutcome;
    private LocalDate writtenStatementDeadline;

    // Criminal-specific
    private String firNumber;
    private LocalDate firDate;
    private String policeStation;
    private String investigationAuthority;
    private LocalDate arrestDate;
    private LocalDate chargeSheetDate;
    private BailStatus bailStatus;
}
