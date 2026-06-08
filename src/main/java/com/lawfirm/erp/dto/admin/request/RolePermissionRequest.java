package com.lawfirm.erp.dto.admin.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class RolePermissionRequest {
    @NotNull(message = "Role ID is required")
    private UUID roleId;

    @NotNull(message = "Permission IDs list is required")
    private List<UUID> permissionIds;
}