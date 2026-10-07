package com.lawfirm.erp.modules.projectmanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateProjectRequest {

    @NotBlank(message = "Project name is required")
    private String name;

    @NotBlank(message = "Client name is required")
    private String clientName;

    private UUID clientUserId;

    private String description;

    private LocalDate startDate;

    private LocalDate targetEndDate;

    private UUID ownerId;
}
