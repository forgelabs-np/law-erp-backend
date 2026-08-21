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

    /** Link to existing client user — nullable for new client flow. */
    private UUID clientUserId;

    private String description;

    private LocalDate startDate;

    private LocalDate targetEndDate;

    /** Owner userId — if null, defaults to the creating user. */
    private UUID ownerId;
}
