package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.request.RoleRequest;
import com.lawfirm.erp.dto.admin.response.RolePermissionResponse;
import com.lawfirm.erp.dto.admin.response.RoleResponse;
import com.lawfirm.erp.dto.firm.response.FirmRolePermissionsResponse;
import com.lawfirm.erp.dto.firm.response.RoleUserResponse;

import java.util.List;
import java.util.UUID;

public interface FirmRoleService {

    List<RoleResponse> getFirmRoles();

    /** Phase 5: SA on-behalf creation — explicit firm id, no firm security context. */
    RoleResponse createRoleForFirm(UUID firmId, RoleRequest request);

    FirmRolePermissionsResponse getRolePermissions(UUID roleId);

    RolePermissionResponse updateRolePermissions(UUID roleId, RolePermissionRequest request);

    List<RoleUserResponse> getRoleUsers(UUID roleId);

    RoleResponse createRole(RoleRequest request);

    void deleteRole(UUID roleId);

    RoleResponse toggleRoleStatus(UUID roleId);
}
