package com.lawfirm.erp.modules.projectmanagement.dto.response;

import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ProjectResponse {

    private UUID id;
    private String projectCode;
    private String name;
    private String clientName;
    private UUID clientUserId;
    private String description;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate targetEndDate;
    private UUID ownerId;
    private String ownerName;

    @Builder.Default
    private List<ProjectMemberResponse> members = new ArrayList<>();

    private int credentialCount;
    private int renewalCount;
    private long overdueInstances;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
