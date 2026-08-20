package com.lawfirm.erp.superadmin.controller;

import com.lawfirm.erp.common.constant.SuperAdminConstants;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.enums.Message;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.admin.response.AdminUserResponse;
import com.lawfirm.erp.dto.auth.request.RegisterSuperAdminRequest;
import com.lawfirm.erp.dto.auth.request.SuperAdminLoginRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.dto.auth.response.RegisterResponse;
import com.lawfirm.erp.superadmin.service.SuperAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/super-admin")
@RequiredArgsConstructor
@Tag(name = "Super Admin", description = "Super Admin Management APIs")
public class SuperAdminController {

    private final SuperAdminService superAdminService;
    private final ResponseHandler responseHandler;

    @PostMapping("/register")
    @Operation(summary = SuperAdminConstants.REGISTER_SUMMARY, description = SuperAdminConstants.REGISTER_DESCRIPTION)
    public ResponseEntity<ApiResponse<RegisterResponse>> registerSuperAdmin(
            @Valid @RequestBody ApiRequest<RegisterSuperAdminRequest> request) {
        return responseHandler.ok(
                superAdminService.registerSuperAdmin(request.getData()),
                Message.CREATE_SUCCESS,
                "Super Admin"
        );
    }

    @PostMapping("/login")
    @Operation(summary = SuperAdminConstants.LOGIN_SUMMARY, description = SuperAdminConstants.LOGIN_DESCRIPTION)
    public ResponseEntity<ApiResponse<LoginResponse>> loginSuperAdmin(
            @Valid @RequestBody ApiRequest<SuperAdminLoginRequest> request) {
        return responseHandler.ok(
                superAdminService.loginSuperAdmin(request.getData()),
                Message.SUCCESS,
                "Authenticated"
        );
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = SuperAdminConstants.GET_USERS_SUMMARY, description = SuperAdminConstants.GET_USERS_DESCRIPTION)
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> getAllUsersWithRoles(
            @RequestParam(required = false) UserType userType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String firmCode) {
        return responseHandler.ok(
                superAdminService.getAllUsersWithRoles(userType, search, firmCode),
                "Users with roles fetched successfully"
        );
    }
}
