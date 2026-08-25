package com.lawfirm.erp.rbac.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.constant.RbacConstants;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.admin.request.PermissionRequest;
import com.lawfirm.erp.dto.admin.response.GroupedPermissionResponse;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.rbac.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/permissions")
@RequiredArgsConstructor
@Tag(name = "Admin - Permission Management", description = "Super Admin permission management APIs")
public class PermissionController {

    private final PermissionService permissionService;
    private final PermissionEvaluator permissionEvaluator;
    private final ResponseHandler responseHandler;

    @PostMapping
    @Operation(summary = RbacConstants.UPSERT_PERMISSION_SUMMARY)
    public ResponseEntity<ApiResponse<PermissionResponse>> upsert(
            @Valid @RequestBody ApiRequest<PermissionRequest> request) {
        permissionEvaluator.require("ROLE_MANAGEMENT:EDIT");
        return responseHandler.ok(
                permissionService.upsert(request.getData()),
                "Permission saved successfully"
        );
    }

    @GetMapping
    @Operation(summary = RbacConstants.GET_ALL_PERMISSIONS_SUMMARY)
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> findAll() {
        permissionEvaluator.require("ROLE_MANAGEMENT:VIEW");
        return responseHandler.ok(
                permissionService.findAll(),
                "Permissions fetched successfully"
        );
    }

    @GetMapping("/grouped")
    @Operation(summary = "Get all permissions grouped by module",
            description = "Returns permissions nested under each module. Use this for the permission management UI — renders a module card with checkboxes for each action.")
    public ResponseEntity<ApiResponse<GroupedPermissionResponse>> findGrouped() {
        permissionEvaluator.require("ROLE_MANAGEMENT:VIEW");
        return responseHandler.ok(
                permissionService.findAllGroupedByModule(),
                "Permissions grouped by module fetched successfully"
        );
    }

    @GetMapping("/active")
    @Operation(summary = RbacConstants.GET_ACTIVE_PERMISSIONS_SUMMARY)
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> findActive() {
        permissionEvaluator.require("ROLE_MANAGEMENT:VIEW");
        return responseHandler.ok(
                permissionService.findActive(),
                "Active permissions fetched successfully"
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = RbacConstants.GET_PERMISSION_BY_ID_SUMMARY)
    public ResponseEntity<ApiResponse<PermissionResponse>> findById(@PathVariable UUID id) {
        permissionEvaluator.require("ROLE_MANAGEMENT:VIEW");
        return responseHandler.ok(
                permissionService.findById(id),
                "Permission fetched successfully"
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = RbacConstants.DELETE_PERMISSION_SUMMARY)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        permissionEvaluator.require("ROLE_MANAGEMENT:DELETE");
        permissionService.delete(id);
        return responseHandler.ok(null, "Permission deleted successfully");
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = RbacConstants.TOGGLE_PERMISSION_SUMMARY)
    public ResponseEntity<ApiResponse<PermissionResponse>> toggle(@PathVariable UUID id) {
        permissionEvaluator.require("ROLE_MANAGEMENT:EDIT");
        return responseHandler.ok(
                permissionService.toggleStatus(id),
                "Permission status toggled successfully"
        );
    }
}