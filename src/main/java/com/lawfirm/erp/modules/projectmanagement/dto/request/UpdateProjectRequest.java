package com.lawfirm.erp.modules.projectmanagement.dto.request;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateProjectRequest {

    private String name;

    private String clientName;

    private UUID clientUserId;

    private String description;

    private LocalDate startDate;

    private LocalDate targetEndDate;

    private UUID ownerId;
}
