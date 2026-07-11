package com.lawfirm.erp.modules.usermanagement.controller;

import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * User Management Module — Firm Admin view.
 *
 * This is the "unified" user layer on top of the existing
 * /firm/employees and /firm/clients endpoints.
 *
 * Those endpoints handle CREATION and MUTATIONS.
 * This module handles READ operations, cross-cutting concerns,
 * and power features (bulk ops, profile, permissions view, activity).
 *
 * Base path: /api/v1/modules/users
 */
@RestController
@RequestMapping("/api/v1/modules/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Unified user management for firm admin — list, search, profile, permissions, activity, bulk ops")
@PreAuthorize("hasRole('FIRM_ADMIN')")
public class UserManagementController {

    private final UserManagementService userManagementService;
    private final ResponseHandler responseHandler;

    // ── List ──────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(
        summary = "List all users in the firm",
        description = "Returns both employees (FIRM_USER) and clients (CLIENT). " +
                      "Filter by userType, roleId, or isActive status."
    )
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> listUsers(
            @RequestParam(required = false) UserType userType,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) Boolean isActive) {

        return responseHandler.ok(
                userManagementService.listUsers(userType, roleId, isActive),
                "Users fetched successfully"
        );
    }

    @GetMapping("/search")
    @Operation(
        summary = "Search users by name, email, username or mobile",
        description = "Case-insensitive partial match across all users in the firm."
    )
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> searchUsers(
            @RequestParam String q) {

        return responseHandler.ok(
                userManagementService.searchUsers(q),
                "Search results fetched"
        );
    }

    // ── Profile ───────────────────────────────────────────────────────────

    @GetMapping("/{userId}/profile")
    @Operation(
        summary = "Get full profile of a user",
        description = "Returns basic info + role + all permissions grouped by module + last 10 audit entries + monthly action count."
    )
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @PathVariable UUID userId) {

        return responseHandler.ok(
                userManagementService.getUserProfile(userId),
                "User profile fetched"
        );
    }

    // ── Permissions ───────────────────────────────────────────────────────

    @GetMapping("/{userId}/permissions")
    @Operation(
        summary = "Get all permissions for a user",
        description = "Shows exactly what this user can do, in a flat list and grouped by module."
    )
    public ResponseEntity<ApiResponse<UserPermissionsResponse>> getUserPermissions(
            @PathVariable UUID userId) {

        return responseHandler.ok(
                userManagementService.getUserPermissions(userId),
                "User permissions fetched"
        );
    }

    // ── Activity ──────────────────────────────────────────────────────────

    @GetMapping("/{userId}/activity")
    @Operation(
        summary = "Get activity timeline for a user",
        description = "Paginated audit log for a specific user within this firm. " +
                      "Supports date range filtering."
    )
    public ResponseEntity<ApiResponse<List<UserProfileResponse.ActivityEntry>>> getUserActivity(
            @PathVariable UUID userId,
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

    // ── Password reset ────────────────────────────────────────────────────

    @PostMapping("/{userId}/reset-password")
    @Operation(
        summary = "Reset a user's password",
        description = "Firm admin resets password for any user in their firm. " +
                      "Immediately invalidates the user's existing JWT — they must re-login."
    )
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable UUID userId,
            @Valid @RequestBody ApiRequest<ResetPasswordRequest> request) {

        userManagementService.resetPassword(userId, request.getData());
        return responseHandler.ok(null, "Password reset successfully. User must re-login.");
    }

    // ── Bulk operations ───────────────────────────────────────────────────

    @PostMapping("/bulk-deactivate")
    @Operation(
        summary = "Deactivate multiple users at once",
        description = "Deactivates each user and invalidates their JWT. " +
                      "Returns per-user success/failure details. " +
                      "Skips: yourself, already inactive users."
    )
    public ResponseEntity<ApiResponse<BulkOperationResult>> bulkDeactivate(
            @Valid @RequestBody ApiRequest<BulkDeactivateRequest> request) {

        return responseHandler.ok(
                userManagementService.bulkDeactivate(request.getData()),
                "Bulk deactivation complete"
        );
    }

    @PostMapping("/bulk-role-change")
    @Operation(
        summary = "Reassign role for multiple users at once",
        description = "Changes the role for all given users. " +
                      "Validates role belongs to this firm. " +
                      "Invalidates JWT for all affected users. " +
                      "Returns per-user success/failure details."
    )
    public ResponseEntity<ApiResponse<BulkOperationResult>> bulkRoleChange(
            @Valid @RequestBody ApiRequest<BulkRoleChangeRequest> request) {

        return responseHandler.ok(
                userManagementService.bulkRoleChange(request.getData()),
                "Bulk role change complete"
        );
    }
}
