package com.lawfirm.erp.rbac.controller;

import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.request.RoleRequest;
import com.lawfirm.erp.dto.admin.response.RolePermissionResponse;
import com.lawfirm.erp.dto.admin.response.RoleResponse;
import com.lawfirm.erp.rbac.service.RoleManagementService;
import com.lawfirm.erp.rbac.service.RolePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
@Tag(name = "Admin - Role Management", description = "Super Admin role management APIs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class RoleController {

    private final RoleManagementService roleManagementService;
    private final RolePermissionService rolePermissionService;
    private final ResponseHandler responseHandler;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping
    @Operation(summary = "Create or update role")
    public ResponseEntity<ApiResponse<RoleResponse>> upsertRole(
            @Valid @RequestBody ApiRequest<RoleRequest> request) {
        return responseHandler.ok(
                roleManagementService.upsertRole(request.getData()),
                "Role saved successfully"
        );
    }

    @GetMapping
    @Operation(summary = "Get all roles")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        return responseHandler.ok(
                roleManagementService.getAllRoles(),
                "Roles fetched successfully"
        );
    }

    @GetMapping("/active")
    @Operation(summary = "Get active roles")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getActiveRoles() {
        return responseHandler.ok(
                roleManagementService.getActiveRoles(),
                "Active roles fetched successfully"
        );
    }

    @GetMapping("/{roleId}")
    @Operation(summary = "Get role by ID")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable UUID roleId) {
        return responseHandler.ok(
                roleManagementService.getRoleById(roleId),
                "Role fetched successfully"
        );
    }

    @DeleteMapping("/{roleId}")
    @Operation(summary = "Delete role")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable UUID roleId) {
        roleManagementService.deleteRole(roleId);
        return responseHandler.ok(null, "Role deleted successfully");
    }

    @PatchMapping("/{roleId}/toggle")
    @Operation(summary = "Toggle role status")
    public ResponseEntity<ApiResponse<RoleResponse>> toggleRoleStatus(@PathVariable UUID roleId) {
        return responseHandler.ok(
                roleManagementService.toggleRoleStatus(roleId),
                "Role status toggled successfully"
        );
    }

    @PostMapping("/permissions")
    @Operation(summary = "Assign permissions to a role")
    public ResponseEntity<ApiResponse<Void>> assignPermissionsToRole(
            @Valid @RequestBody ApiRequest<RolePermissionRequest> request) {
        rolePermissionService.assignPermissionsToRole(request.getData());
        return responseHandler.ok(null, "Permissions assigned to role successfully");
    }

    @GetMapping("/{roleId}/permissions")
    @Operation(summary = "Get permissions for a role")
    public ResponseEntity<ApiResponse<RolePermissionResponse>> getRolePermissions(@PathVariable UUID roleId) {
        return responseHandler.ok(
                rolePermissionService.getRolePermissions(roleId),
                "Role permissions fetched successfully"
        );
    }
}