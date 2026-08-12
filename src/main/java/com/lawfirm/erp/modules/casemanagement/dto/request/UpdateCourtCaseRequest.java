package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.BailStatus;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateCourtCaseRequest {

    private String courtCaseNumber;

    private String courtName;

    private UUID advocateId;

    private String judgeName;

    // Criminal trial-level
    private String firNumber;

    private LocalDate firDate;

    private String policeStation;

    private String investigationAuthority;

    private LocalDate arrestDate;

    private LocalDate chargeSheetDate;

    private BailStatus bailStatus;

    // Civil trial-level
    private LocalDate mediationDate;

    private String mediationOutcome;

    private LocalDate writtenStatementDeadline;
}
