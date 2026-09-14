package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CourtCaseResponse {

    private UUID id;

    private UUID matterId;

    private String matterNumber;

    private String matterTitle;

    private UUID parentCourtCaseId;

    private RelationType relationType;

    private CourtLevel courtLevel;

    private String courtName;

    private String courtCaseNumber;

    private String ourCourtCaseRef;

    private LocalDate filingDate;

    private CourtCaseStage stage;

    private CourtCaseStatus status;

    private UUID advocateId;

    private String judgeName;

    // Judgment / appeal deadline
    private LocalDate judgmentDate;

    private String judgmentSummary;

    private UUID decisionInFavorOfPartyId;

    private LocalDate appealDeadline;

    private boolean partyIsState;

    /** True for High Court judgments — a further appeal needs a leave petition first. */
    private boolean appealRequiresLeave;

    private boolean appealLapsed;

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

    private List<CourtCaseRoleResponse> roles = new ArrayList<>();

    private int eventCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
