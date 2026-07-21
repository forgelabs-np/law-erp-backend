package com.lawfirm.erp.auth.controller;

import com.lawfirm.erp.auth.service.AuthService;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.enums.Message;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.auth.request.*;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication APIs")
public class AuthController {

    private final AuthService authService;
    private final ResponseHandler responseHandler;

    @PostMapping("/login")
    @Operation(summary = "API to internal user login", description = "API to login for internal users")
    public ResponseEntity<ApiResponse<LoginResponse>> authenticateInternalUser(
            @Valid @RequestBody ApiRequest<LoginRequest> request) {
        return responseHandler.ok(
                authService.authenticateInternalUser(request.getData()),
                Message.SUCCESS,
                "Authenticated"
        );
    }

    @PostMapping("/client/login")
    @Operation(summary = "API to client login", description = "API to login for client users")
    public ResponseEntity<ApiResponse<LoginResponse>> authenticateClient(
            @Valid @RequestBody ApiRequest<LoginRequest> request) {
        return responseHandler.ok(
                authService.authenticateClient(request.getData()),
                Message.SUCCESS,
                "Authenticated"
        );
    }

    // ========== REGISTRATION ENDPOINTS - DISABLED (Super Admin only creates) ==========

    // @PostMapping("/register/solo")
    // @Operation(summary = "API to register solo lawyer", description = "Register a new solo practitioner")
    // public ResponseEntity<ApiResponse<RegisterResponse>> registerSolo(
    //         @Valid @RequestBody ApiRequest<RegisterSoloRequest> request) {
    //     return responseHandler.ok(
    //             authService.registerSolo(request.getData()),
    //             Message.CREATE_SUCCESS,
    //             "Solo Lawyer"
    //     );
    // }

    // @PostMapping("/register/client")
    // @Operation(summary = "API to register client", description = "Register a new client")
    // public ResponseEntity<ApiResponse<RegisterResponse>> registerClient(
    //         @Valid @RequestBody ApiRequest<RegisterClientRequest> request) {
    //     return responseHandler.ok(
    //             authService.registerClient(request.getData()),
    //             Message.CREATE_SUCCESS,
    //             "Client"
    //     );
    // }

    @PostMapping("/refresh")
    @Operation(summary = "API to refresh token", description = "Get new access token using refresh token")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @Valid @RequestBody ApiRequest<RefreshTokenRequest> request) {
        return responseHandler.ok(
                authService.refreshToken(request.getData().getRefreshToken()),
                Message.SUCCESS,
                "Token refreshed"
        );
    }

    @PostMapping("/mfa/setup/confirm")
    @Operation(summary = "Confirm MFA setup after scanning QR code")
    public ResponseEntity<ApiResponse<LoginResponse>> confirmMfaSetup(
            @Valid @RequestBody ApiRequest<MfaSetupConfirmRequest> request) {
        return responseHandler.ok(
                authService.confirmMfaSetup(request.getData()),
                Message.SUCCESS,
                "MFA enabled"
        );
    }

    @PostMapping("/mfa/validate")
    @Operation(summary = "Validate TOTP code on login")
    public ResponseEntity<ApiResponse<LoginResponse>> validateMfa(
            @Valid @RequestBody ApiRequest<MfaValidateRequest> request) {
        return responseHandler.ok(
                authService.validateMfa(request.getData()),
                Message.SUCCESS,
                "Authenticated"
        );
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password on first login")
    public ResponseEntity<ApiResponse<LoginResponse>> changePassword(
            @Valid @RequestBody ApiRequest<ChangePasswordRequest> request) {
        return responseHandler.ok(
                authService.changePassword(request.getData()),
                Message.SUCCESS,
                "Password changed"
        );
    }

//    @PostMapping("/mfa/bulk-enable")
//    @PreAuthorize("hasRole('FIRM_ADMIN') or hasRole('SUPER_ADMIN')")
//    public ResponseEntity<ApiResponse<Void>> bulkEnableMfa(
//            @Valid @RequestBody ApiRequest<BulkEnableMfaRequest> request) {
//        authService.bulkEnableMfa(request.getData());
//        return responseHandler.ok(null, Message.SUCCESS, "MFA enabled for selected users");
//    }
}