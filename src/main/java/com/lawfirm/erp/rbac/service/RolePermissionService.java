package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.response.RolePermissionResponse;

import java.util.UUID;

public interface RolePermissionService {

    void assignPermissionsToRole(RolePermissionRequest request);

    RolePermissionResponse getRolePermissions(UUID roleId);
}
