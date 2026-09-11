package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.BailStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CaseStage;
import com.lawfirm.erp.modules.casemanagement.enums.CaseStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CaseType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CaseResponse {
    private UUID id;
    private UUID firmId;
    private String caseNumber;
    private CaseType caseType;
    private String title;
    private CaseStage caseStage;
    private CaseStatus status;
    private String courtName;
    private String courtCaseNumber;
    private String judgeName;
    private LocalDate filingDate;
    private String filingNumber;
    private UUID assignedTo;
    private String description;
    private List<PartyResponse> parties;
    private int hearingCount;

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

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
