// controller/RoleController.java
package com.lawfirm.erp.controller;

import com.lawfirm.erp.dto.ApiRequest;
import com.lawfirm.erp.dto.ApiResponse;
import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.request.RoleRequest;
import com.lawfirm.erp.dto.admin.response.RolePermissionResponse;
import com.lawfirm.erp.dto.admin.response.RoleResponse;
import com.lawfirm.erp.exception.ResponseHandler;
import com.lawfirm.erp.service.RoleManagementService;
import com.lawfirm.erp.service.RolePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
@Tag(name = "Admin - Role Management", description = "Super Admin role management APIs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class RoleController {

    private final RoleManagementService roleManagementService;
    private final RolePermissionService rolePermissionService;
    private final ResponseHandler responseHandler;

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.lawfirm.erp.entity.User) {
            return ((com.lawfirm.erp.entity.User) auth.getPrincipal()).getId();
        }
        return 1L;
    }

    @PostMapping
    @Operation(summary = "Create or update role", description = "Creates new role or updates existing one")
    public ResponseEntity<ApiResponse<RoleResponse>> upsertRole(
            @Valid @RequestBody ApiRequest<RoleRequest> request) {
        return responseHandler.ok(
                roleManagementService.upsertRole(request.getData(), getCurrentUserId()),
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

    @GetMapping("/{roleId}")
    @Operation(summary = "Get role by ID")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable Long roleId) {
        return responseHandler.ok(
                roleManagementService.getRoleById(roleId),
                "Role fetched successfully"
        );
    }

    @DeleteMapping("/{roleId}")
    @Operation(summary = "Delete role")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Long roleId) {
        roleManagementService.deleteRole(roleId, getCurrentUserId());
        return responseHandler.ok(null, "Role deleted successfully");
    }

    @PatchMapping("/{roleId}/toggle")
    @Operation(summary = "Toggle role status")
    public ResponseEntity<ApiResponse<RoleResponse>> toggleRoleStatus(@PathVariable Long roleId) {
        return responseHandler.ok(
                roleManagementService.toggleRoleStatus(roleId, getCurrentUserId()),
                "Role status toggled successfully"
        );
    }

    @PostMapping("/permissions")
    @Operation(summary = "Assign permissions to a role")
    public ResponseEntity<ApiResponse<Void>> assignPermissionsToRole(
            @Valid @RequestBody ApiRequest<RolePermissionRequest> request) {
        rolePermissionService.assignPermissionsToRole(request.getData(), getCurrentUserId());
        return responseHandler.ok(null, "Permissions assigned to role successfully");
    }

    @GetMapping("/{roleId}/permissions")
    @Operation(summary = "Get permissions for a role")
    public ResponseEntity<ApiResponse<RolePermissionResponse>> getRolePermissions(@PathVariable Long roleId) {
        return responseHandler.ok(
                rolePermissionService.getRolePermissions(roleId),
                "Role permissions fetched successfully"
        );
    }
}