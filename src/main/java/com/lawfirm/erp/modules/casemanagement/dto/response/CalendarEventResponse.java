package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.CourtEventStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
public class CalendarEventResponse {

    private UUID id;

    private UUID courtCaseId;

    private String ourCourtCaseRef;

    private String matterNumber;

    private String matterTitle;

    private CourtEventType eventType;

    private LocalDate scheduledDate;

    private LocalTime scheduledTime;

    private LocalTime endTime;

    private String courtRoom;

    private CourtEventStatus status;

    private UUID attendingAdvocateId;
}
