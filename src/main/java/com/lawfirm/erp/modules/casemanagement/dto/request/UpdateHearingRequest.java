package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.HearingStatus;
import com.lawfirm.erp.modules.casemanagement.enums.HearingType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class UpdateHearingRequest {

    private String title;
    private LocalDate date;
    private LocalTime time;
    private LocalTime endTime;
    private String courtRoom;
    private String judgeName;
    private HearingType hearingType;
    private HearingStatus status;
    private String outcome;
    private String notes;
    private String attendees;
    private UUID advocateId;
}
