package com.lawfirm.erp.dto.admin.request;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class RolePermissionRequest {
    private UUID roleId;
    private List<UUID> permissionIds;
}