package com.lawfirm.erp.modules.projectmanagement.dto.request;

import com.lawfirm.erp.modules.projectmanagement.enums.RenewalRecurrence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateRenewalRequest {

    @NotNull(message = "Renewal type ID is required")
    private Long renewalTypeId;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Recurrence is required")
    private RenewalRecurrence recurrence;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    /** NULL = ongoing recurring (generate 3 years ahead by default). */
    private LocalDate endDate;

    private UUID assignedToId;
}
