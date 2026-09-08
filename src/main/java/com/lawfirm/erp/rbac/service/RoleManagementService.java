package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.dto.admin.request.RoleRequest;
import com.lawfirm.erp.dto.admin.response.RoleResponse;

import java.util.List;
import java.util.UUID;

public interface RoleManagementService {

    RoleResponse upsertRole(RoleRequest request);

    void deleteRole(UUID roleId);

    RoleResponse toggleRoleStatus(UUID roleId);

    List<RoleResponse> getAllRoles();

    List<RoleResponse> getActiveRoles();

    /** System role templates (firm IS NULL, isSystem=true) with current permissions. */
    List<RoleResponse> getSystemTemplates();

    RoleResponse getRoleById(UUID roleId);
}
