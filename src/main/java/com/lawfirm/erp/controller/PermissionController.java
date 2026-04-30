// controller/PermissionController.java
package com.lawfirm.erp.controller;

import com.lawfirm.erp.dto.ApiRequest;
import com.lawfirm.erp.dto.ApiResponse;
import com.lawfirm.erp.dto.admin.request.PermissionRequest;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.exception.ResponseHandler;
import com.lawfirm.erp.service.PermissionService;
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
@RequestMapping("/api/v1/admin/permissions")
@RequiredArgsConstructor
@Tag(name = "Admin - Permission Management", description = "Super Admin permission management APIs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PermissionController {

    private final PermissionService permissionService;
    private final ResponseHandler responseHandler;

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.lawfirm.erp.entity.User) {
            return ((com.lawfirm.erp.entity.User) auth.getPrincipal()).getId();
        }
        return 1L;
    }

    @PostMapping
    @Operation(summary = "Create or update permission", description = "Creates new permission or updates existing one")
    public ResponseEntity<ApiResponse<PermissionResponse>> upsertPermission(
            @Valid @RequestBody ApiRequest<PermissionRequest> request) {
        return responseHandler.ok(
                permissionService.upsertPermission(request.getData(), getCurrentUserId()),
                "Permission saved successfully"
        );
    }

    @GetMapping
    @Operation(summary = "Get all permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        return responseHandler.ok(
                permissionService.getAllPermissions(),
                "Permissions fetched successfully"
        );
    }

    @GetMapping("/active")
    @Operation(summary = "Get active permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getActivePermissions() {
        return responseHandler.ok(
                permissionService.getActivePermissions(),
                "Active permissions fetched successfully"
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get permission by ID")
    public ResponseEntity<ApiResponse<PermissionResponse>> getPermissionById(@PathVariable Long id) {
        return responseHandler.ok(
                permissionService.getPermissionById(id),
                "Permission fetched successfully"
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete permission")
    public ResponseEntity<ApiResponse<Void>> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id, getCurrentUserId());
        return responseHandler.ok(null, "Permission deleted successfully");
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Toggle permission status")
    public ResponseEntity<ApiResponse<PermissionResponse>> togglePermissionStatus(@PathVariable Long id) {
        return responseHandler.ok(
                permissionService.togglePermissionStatus(id, getCurrentUserId()),
                "Permission status toggled successfully"
        );
    }
}