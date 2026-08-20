package com.lawfirm.erp.superadmin.service;

import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.dto.admin.response.AdminUserResponse;
import com.lawfirm.erp.dto.auth.request.RegisterSuperAdminRequest;
import com.lawfirm.erp.dto.auth.request.SuperAdminLoginRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.dto.auth.response.RegisterResponse;

import java.util.List;

public interface SuperAdminService {

    RegisterResponse registerSuperAdmin(RegisterSuperAdminRequest request);

    LoginResponse loginSuperAdmin(SuperAdminLoginRequest request);

    List<AdminUserResponse> getAllUsersWithRoles(UserType userType, String search, String firmCode);
}
