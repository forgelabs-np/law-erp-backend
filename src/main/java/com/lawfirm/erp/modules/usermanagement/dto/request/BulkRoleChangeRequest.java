package com.lawfirm.erp.modules.usermanagement.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BulkRoleChangeRequest {
    @NotEmpty(message = "At least one user ID is required")
    private List<UUID> userIds;

    @NotNull(message = "Role ID is required")
    private UUID roleId;
}
