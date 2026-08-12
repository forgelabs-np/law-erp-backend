package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.CourtEventType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class ScheduleCourtEventRequest {

    @NotNull(message = "Event type is required")
    private CourtEventType eventType;

    @NotNull(message = "Scheduled date is required")
    private LocalDate scheduledDate;

    private LocalTime scheduledTime;

    private LocalTime endTime;

    private UUID attendingAdvocateId;

    private String judgeName;

    private String courtRoom;

    private String notes;
}
