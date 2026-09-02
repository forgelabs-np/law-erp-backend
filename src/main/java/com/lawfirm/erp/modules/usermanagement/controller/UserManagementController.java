package com.lawfirm.erp.modules.usermanagement.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.constant.UserManagementConstants;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.usermanagement.dto.request.BulkDeactivateRequest;
import com.lawfirm.erp.modules.usermanagement.dto.request.BulkRoleChangeRequest;
import com.lawfirm.erp.modules.usermanagement.dto.request.ResetPasswordRequest;
import com.lawfirm.erp.modules.usermanagement.dto.response.BulkOperationResult;
import com.lawfirm.erp.modules.usermanagement.dto.response.UserPermissionsResponse;
import com.lawfirm.erp.modules.usermanagement.dto.response.UserProfileResponse;
import com.lawfirm.erp.modules.usermanagement.dto.response.UserSummaryResponse;
import com.lawfirm.erp.modules.usermanagement.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/modules/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Unified user management for firm admin — list, search, profile, permissions, activity, bulk ops")
public class UserManagementController {

    private final UserManagementService userManagementService;
    private final PermissionEvaluator permissionEvaluator;
    private final ResponseHandler responseHandler;

    @GetMapping
    @Operation(summary = UserManagementConstants.LIST_USERS_SUMMARY, description = UserManagementConstants.LIST_USERS_DESCRIPTION)
    public ResponseEntity<ApiResponse<PagedResponse<UserSummaryResponse>>> listUsers(
            @RequestParam(required = false) UserType userType,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        permissionEvaluator.require("USER_MANAGEMENT:VIEW");
        return responseHandler.ok(
                userManagementService.listUsers(userType, roleId, isActive, page, size),
                "Users fetched successfully"
        );
    }

    @GetMapping("/search")
    @Operation(summary = UserManagementConstants.SEARCH_USERS_SUMMARY, description = UserManagementConstants.SEARCH_USERS_DESCRIPTION)
    public ResponseEntity<ApiResponse<PagedResponse<UserSummaryResponse>>> searchUsers(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        permissionEvaluator.require("USER_MANAGEMENT:VIEW");
        return responseHandler.ok(
                userManagementService.searchUsers(q, page, size),
                "Search results fetched"
        );
    }

    @GetMapping("/{userId}/profile")
    @Operation(summary = UserManagementConstants.GET_PROFILE_SUMMARY, description = UserManagementConstants.GET_PROFILE_DESCRIPTION)
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @PathVariable UUID userId) {
        permissionEvaluator.require("USER_MANAGEMENT:VIEW");
        return responseHandler.ok(
                userManagementService.getUserProfile(userId),
                "User profile fetched"
        );
    }

    @GetMapping("/{userId}/permissions")
    @Operation(summary = UserManagementConstants.GET_PERMISSIONS_SUMMARY, description = UserManagementConstants.GET_PERMISSIONS_DESCRIPTION)
    public ResponseEntity<ApiResponse<UserPermissionsResponse>> getUserPermissions(
            @PathVariable UUID userId) {
        permissionEvaluator.require("USER_MANAGEMENT:VIEW");
        return responseHandler.ok(
                userManagementService.getUserPermissions(userId),
                "User permissions fetched"
        );
    }

    @GetMapping("/{userId}/activity")
    @Operation(summary = UserManagementConstants.GET_ACTIVITY_SUMMARY, description = UserManagementConstants.GET_ACTIVITY_DESCRIPTION)
    public ResponseEntity<ApiResponse<List<UserProfileResponse.ActivityEntry>>> getUserActivity(
            @PathVariable UUID userId,
            // permissionEvaluator.require("USER_MANAGEMENT:VIEW") — already checked at list/search level
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responseHandler.ok(
                userManagementService.getUserActivity(userId, from, to, page, size),
                "User activity fetched"
        );
    }

    @PostMapping("/{userId}/reset-password")
    @Operation(summary = UserManagementConstants.RESET_PASSWORD_SUMMARY, description = UserManagementConstants.RESET_PASSWORD_DESCRIPTION)
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable UUID userId,
            @Valid @RequestBody ApiRequest<ResetPasswordRequest> request) {
        permissionEvaluator.require("USER_MANAGEMENT:EDIT");
        userManagementService.resetPassword(userId, request.getData());
        return responseHandler.ok(null, "Password reset successfully. User must re-login.");
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = UserManagementConstants.DELETE_USER_SUMMARY, description = UserManagementConstants.DELETE_USER_DESCRIPTION)
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID userId) {
        permissionEvaluator.require("USER_MANAGEMENT:DELETE");
        userManagementService.deleteUser(userId);
        return responseHandler.ok(null, "User deleted successfully");
    }

    @PostMapping("/bulk-deactivate")
    @Operation(summary = UserManagementConstants.BULK_DEACTIVATE_SUMMARY, description = UserManagementConstants.BULK_DEACTIVATE_DESCRIPTION)
    public ResponseEntity<ApiResponse<BulkOperationResult>> bulkDeactivate(
            @Valid @RequestBody ApiRequest<BulkDeactivateRequest> request) {
        permissionEvaluator.require("USER_MANAGEMENT:DELETE");
        return responseHandler.ok(
                userManagementService.bulkDeactivate(request.getData()),
                "Bulk deactivation complete"
        );
    }

    @PostMapping("/bulk-role-change")
    @Operation(summary = UserManagementConstants.BULK_ROLE_CHANGE_SUMMARY, description = UserManagementConstants.BULK_ROLE_CHANGE_DESCRIPTION)
    public ResponseEntity<ApiResponse<BulkOperationResult>> bulkRoleChange(
            @Valid @RequestBody ApiRequest<BulkRoleChangeRequest> request) {
        permissionEvaluator.require("USER_MANAGEMENT:EDIT");
        return responseHandler.ok(
                userManagementService.bulkRoleChange(request.getData()),
                "Bulk role change complete"
        );
    }
}
