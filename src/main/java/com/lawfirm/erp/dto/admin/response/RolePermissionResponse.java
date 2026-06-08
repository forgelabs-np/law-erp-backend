package com.lawfirm.erp.dto.admin.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RolePermissionResponse {
    private UUID roleId;
    private String roleName;
    private String roleCode;
    private List<PermissionResponse> permissions;
}