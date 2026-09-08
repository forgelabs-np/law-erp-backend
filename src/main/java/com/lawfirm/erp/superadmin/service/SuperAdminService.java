package com.lawfirm.erp.superadmin.service;

import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.response.AdminUserResponse;
import com.lawfirm.erp.dto.admin.response.RolePermissionResponse;
import com.lawfirm.erp.dto.admin.response.RoleResponse;
import com.lawfirm.erp.dto.auth.request.MfaResetRequest;
import com.lawfirm.erp.dto.auth.request.RegisterSuperAdminRequest;
import com.lawfirm.erp.dto.auth.request.SuperAdminLoginRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.dto.auth.response.RegisterResponse;

import java.util.List;
import java.util.UUID;

public interface SuperAdminService {

    RegisterResponse registerSuperAdmin(RegisterSuperAdminRequest request);

    LoginResponse loginSuperAdmin(SuperAdminLoginRequest request);

    PagedResponse<AdminUserResponse> getAllUsersWithRoles(UserType userType, String search, String firmCode, int page, int size);

    void resetMfa(MfaResetRequest request);

    RolePermissionResponse overrideRolePermissions(UUID firmId, UUID roleId, RolePermissionRequest request);

    /** SA discoverability: a firm's roles + current permissions + holder counts. */
    List<RoleResponse> getFirmRoles(UUID firmId);
}
