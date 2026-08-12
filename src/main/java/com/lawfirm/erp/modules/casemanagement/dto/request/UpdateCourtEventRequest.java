package com.lawfirm.erp.modules.casemanagement.dto.request;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class UpdateCourtEventRequest {

    private LocalDate scheduledDate;

    private LocalTime scheduledTime;

    private LocalTime endTime;

    private UUID attendingAdvocateId;

    private String judgeName;

    private String courtRoom;

    private String notes;
}
