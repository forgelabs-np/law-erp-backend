package com.lawfirm.erp.controller;

import com.lawfirm.erp.dto.ApiRequest;
import com.lawfirm.erp.dto.ApiResponse;
import com.lawfirm.erp.dto.auth.request.LoginRequest;
import com.lawfirm.erp.dto.auth.request.RefreshTokenRequest;
import com.lawfirm.erp.dto.auth.request.RegisterClientRequest;
import com.lawfirm.erp.dto.auth.request.RegisterSoloRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.dto.auth.response.RegisterResponse;
import com.lawfirm.erp.enums.Message;
import com.lawfirm.erp.exception.ResponseHandler;
import com.lawfirm.erp.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/register/solo")
    @Operation(summary = "API to register solo lawyer", description = "Register a new solo practitioner")
    public ResponseEntity<ApiResponse<RegisterResponse>> registerSolo(
            @Valid @RequestBody ApiRequest<RegisterSoloRequest> request) {
        return responseHandler.ok(
                authService.registerSolo(request.getData()),
                Message.CREATE_SUCCESS,
                "Solo Lawyer"
        );
    }

    @PostMapping("/register/client")
    @Operation(summary = "API to register client", description = "Register a new client")
    public ResponseEntity<ApiResponse<RegisterResponse>> registerClient(
            @Valid @RequestBody ApiRequest<RegisterClientRequest> request) {
        return responseHandler.ok(
                authService.registerClient(request.getData()),
                Message.CREATE_SUCCESS,
                "Client"
        );
    }

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
}