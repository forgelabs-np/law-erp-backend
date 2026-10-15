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
}
