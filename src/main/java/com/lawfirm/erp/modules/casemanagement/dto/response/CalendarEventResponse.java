package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.HearingStatus;
import com.lawfirm.erp.modules.casemanagement.enums.HearingType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
public class CalendarEventResponse {
    private UUID id;
    private UUID caseId;
    private String caseNumber;
    private String caseTitle;
    private String title;
    private LocalDate date;
    private LocalTime time;
    private LocalTime endTime;
    private String courtRoom;
    private HearingType hearingType;
    private HearingStatus status;
    private UUID advocateId;
}
