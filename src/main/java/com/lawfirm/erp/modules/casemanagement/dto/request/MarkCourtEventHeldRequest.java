package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.NextEventType;
import com.lawfirm.erp.modules.casemanagement.enums.OutcomeType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class MarkCourtEventHeldRequest {

    private String outcome;

    @NotNull(message = "Outcome type is required")
    private OutcomeType outcomeType;

    @NotNull(message = "Next event type is required")
    private NextEventType nextEventType;

    private LocalDate nextEventDate;

    private LocalTime nextEventTime;

    private String notes;


    private LocalDate judgmentDate;

    private String judgmentSummary;

    private UUID decisionInFavorOfPartyId;
}
