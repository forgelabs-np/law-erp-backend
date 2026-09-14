package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.NextEventType;
import com.lawfirm.erp.modules.casemanagement.enums.OutcomeType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Marking an event HELD forces the "what did the court give next?" answer —
 * the direct digital equivalent of writing the next date in the diary.
 */
@Data
public class MarkCourtEventHeldRequest {

    private String outcome;

    @NotNull(message = "Outcome type is required")
    private OutcomeType outcomeType;

    @NotNull(message = "Next event type is required")
    private NextEventType nextEventType;

    /** Required when nextEventType is TARIK/PESHI/JUDGMENT. */
    private LocalDate nextEventDate;

    private LocalTime nextEventTime;

    private String notes;

    // Inline judgment fields — REQUIRED when outcomeType == JUDGMENT_DELIVERED, so a case
    // can only become DECIDED with the judgment on record (the two paths cannot drift).

    private LocalDate judgmentDate;

    private String judgmentSummary;

    private UUID decisionInFavorOfPartyId;
}
