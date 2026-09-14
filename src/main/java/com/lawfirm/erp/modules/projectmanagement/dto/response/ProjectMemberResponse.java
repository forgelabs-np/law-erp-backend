package com.lawfirm.erp.modules.projectmanagement.dto.response;

import com.lawfirm.erp.modules.projectmanagement.enums.ProjectMemberRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ProjectMemberResponse {

    private Long id;
    private UUID userId;
    private String userName;
    private String userEmail;
    private ProjectMemberRole roleInProject;
    private LocalDateTime addedAt;
}
