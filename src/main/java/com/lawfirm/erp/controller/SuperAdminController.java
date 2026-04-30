package com.lawfirm.erp.controller;

import com.lawfirm.erp.dto.ApiRequest;
import com.lawfirm.erp.dto.ApiResponse;
import com.lawfirm.erp.dto.auth.request.LoginRequest;
import com.lawfirm.erp.dto.auth.request.RegisterSuperAdminRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.dto.auth.response.RegisterResponse;
import com.lawfirm.erp.enums.Message;
import com.lawfirm.erp.exception.ResponseHandler;
import com.lawfirm.erp.service.SuperAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/super-admin")
@RequiredArgsConstructor
@Tag(name = "Super Admin", description = "Super Admin Management APIs")
public class SuperAdminController {

    private final SuperAdminService superAdminService;
    private final ResponseHandler responseHandler;

    @PostMapping("/register")
    @Operation(summary = "Register Super Admin (One-time only)", description = "Creates the first super admin. Will fail if already exists.")
    public ResponseEntity<ApiResponse<RegisterResponse>> registerSuperAdmin(
            @Valid @RequestBody ApiRequest<RegisterSuperAdminRequest> request) {
        return responseHandler.ok(
                superAdminService.registerSuperAdmin(request.getData()),
                Message.CREATE_SUCCESS,
                "Super Admin"
        );
    }

    @PostMapping("/login")
    @Operation(summary = "Super Admin Login", description = "Login for super admin - ONLY endpoint they can use")
    public ResponseEntity<ApiResponse<LoginResponse>> loginSuperAdmin(
            @Valid @RequestBody ApiRequest<LoginRequest> request) {
        return responseHandler.ok(
                superAdminService.loginSuperAdmin(request.getData()),
                Message.SUCCESS,
                "Authenticated"
        );
    }
}
