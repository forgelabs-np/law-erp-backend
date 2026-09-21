package com.lawfirm.erp.auth.controller;

import com.lawfirm.erp.auth.service.AuthService;
import com.lawfirm.erp.common.constant.AuthConstants;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication APIs")
public class AuthController {

    private final AuthService authService;
    private final ResponseHandler responseHandler;

    @PostMapping("/login")
    @Operation(summary = AuthConstants.LOGIN_SUMMARY, description = AuthConstants.LOGIN_DESCRIPTION)
    public ResponseEntity<ApiResponse<LoginResponse>> authenticateInternalUser(
            @Valid @RequestBody ApiRequest<LoginRequest> request) {
        return responseHandler.ok(
                authService.authenticateInternalUser(request.getData()),
                Message.SUCCESS,
                "Authenticated"
        );
    }

    @PostMapping("/client/login")
    @Operation(summary = AuthConstants.CLIENT_LOGIN_SUMMARY, description = AuthConstants.CLIENT_LOGIN_DESCRIPTION)
    public ResponseEntity<ApiResponse<LoginResponse>> authenticateClient(
            @Valid @RequestBody ApiRequest<LoginRequest> request) {
        return responseHandler.ok(
                authService.authenticateClient(request.getData()),
                Message.SUCCESS,
                "Authenticated"
        );
    }

    @PostMapping("/refresh")
    @Operation(summary = AuthConstants.REFRESH_TOKEN_SUMMARY, description = AuthConstants.REFRESH_TOKEN_DESCRIPTION)
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @Valid @RequestBody ApiRequest<RefreshTokenRequest> request) {
        return responseHandler.ok(
                authService.refreshToken(request.getData().getRefreshToken()),
                Message.SUCCESS,
                "Token refreshed"
        );
    }

    @PostMapping("/mfa/setup/confirm")
    @Operation(summary = AuthConstants.MFA_SETUP_CONFIRM_SUMMARY, description = AuthConstants.MFA_SETUP_CONFIRM_DESCRIPTION)
    public ResponseEntity<ApiResponse<LoginResponse>> confirmMfaSetup(
            @Valid @RequestBody ApiRequest<MfaSetupConfirmRequest> request) {
        return responseHandler.ok(
                authService.confirmMfaSetup(request.getData()),
                Message.SUCCESS,
                "MFA enabled"
        );
    }

    @PostMapping("/mfa/validate")
    @Operation(summary = AuthConstants.MFA_VALIDATE_SUMMARY, description = AuthConstants.MFA_VALIDATE_DESCRIPTION)
    public ResponseEntity<ApiResponse<LoginResponse>> validateMfa(
            @Valid @RequestBody ApiRequest<MfaValidateRequest> request) {
        return responseHandler.ok(
                authService.validateMfa(request.getData()),
                Message.SUCCESS,
                "Authenticated"
        );
    }

    @PostMapping("/change-password")
    @Operation(summary = AuthConstants.CHANGE_PASSWORD_SUMMARY, description = AuthConstants.CHANGE_PASSWORD_DESCRIPTION)
    public ResponseEntity<ApiResponse<LoginResponse>> changePassword(
            @Valid @RequestBody ApiRequest<ChangePasswordRequest> request) {
        return responseHandler.ok(
                authService.changePassword(request.getData()),
                Message.SUCCESS,
                "Password changed"
        );
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Start self-service password recovery",
            description = "Emails a one-time reset link. Always reports success so the endpoint "
                    + "cannot be used to discover which accounts exist.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ApiRequest<ForgotPasswordRequest> request) {
        authService.forgotPassword(request.getData());
        return responseHandler.ok(null, Message.SUCCESS,
                "If the account exists, a password reset link has been sent to its e-mail address");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Complete password recovery with the one-time link token",
            description = "Sets the new password, clears any lockout and revokes existing sessions")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ApiRequest<PasswordResetRequest> request) {
        authService.resetPasswordWithToken(request.getData());
        return responseHandler.ok(null, Message.SUCCESS,
                "Password updated. Please sign in with your new password.");
    }
}
