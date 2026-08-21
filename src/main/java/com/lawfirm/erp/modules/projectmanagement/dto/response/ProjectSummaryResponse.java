package com.lawfirm.erp.modules.projectmanagement.dto.response;

import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ProjectSummaryResponse {

    private UUID id;
    private String projectCode;
    private String name;
    private String clientName;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate targetEndDate;
    private String ownerName;
    private int credentialCount;
    private int renewalCount;
    private long overdueInstances;
    private LocalDateTime createdAt;
}
