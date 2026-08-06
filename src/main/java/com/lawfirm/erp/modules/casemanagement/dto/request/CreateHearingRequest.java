package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.lawfirm.erp.modules.casemanagement.enums.HearingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class CreateHearingRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Date is required")
    private LocalDate date;

    @JsonAlias({"startTime", "startingTime", "start"})
    private LocalTime time;
    private LocalTime endTime;
    private String courtRoom;
    private String judgeName;

    @NotNull(message = "Hearing type is required")
    private HearingType hearingType;

    private String notes;
    private String attendees;
    private UUID advocateId;
}
