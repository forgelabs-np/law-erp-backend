package com.lawfirm.erp.modules.projectmanagement.dto.request;

import com.lawfirm.erp.modules.projectmanagement.enums.ProjectMemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddMemberRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Role is required")
    private ProjectMemberRole role;
}
