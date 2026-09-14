package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.CourtEventStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventType;
import com.lawfirm.erp.modules.casemanagement.enums.NextEventType;
import com.lawfirm.erp.modules.casemanagement.enums.OutcomeType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
public class CourtEventResponse {

    private UUID id;

    private UUID courtCaseId;

    private String ourCourtCaseRef;

    private String matterNumber;

    private String matterTitle;

    private CourtEventType eventType;

    private int sequenceNo;

    private LocalDate scheduledDate;

    private LocalTime scheduledTime;

    private LocalTime endTime;

    private CourtEventStatus status;

    private String outcome;

    private OutcomeType outcomeType;

    private NextEventType nextEventType;

    private UUID nextEventId;

    private UUID attendingAdvocateId;

    private String judgeName;

    private String courtRoom;

    private String notes;

    /** Populated when a Tarik event was scheduled despite an advocate overlap (Peshi hard-blocks). */
    private String conflictWarning;

    private LocalDateTime createdAt;
}
