package com.lawfirm.erp.modules.casemanagement.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class RecordJudgmentRequest {

    @NotNull(message = "Judgment date is required")
    private LocalDate judgmentDate;

    private String judgmentSummary;

    private UUID decisionInFavorOfPartyId;

    // NOTE: partyIsState is intentionally NOT here. It is recorded once at court-case
    // creation (AddCourtCaseRequest.partyIsState) and the appeal-deadline engine always
    // trusts that stored value — a per-call flag here could silently disagree with it.
}
