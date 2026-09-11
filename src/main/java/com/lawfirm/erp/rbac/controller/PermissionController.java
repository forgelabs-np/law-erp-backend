package com.lawfirm.erp.rbac.controller;

import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.admin.request.PermissionRequest;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.rbac.service.PermissionService;
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
@RequestMapping("/api/v1/admin/permissions")
@RequiredArgsConstructor
@Tag(name = "Admin - Permission Management", description = "Super Admin permission management APIs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PermissionController {

    private final PermissionService permissionService;
    private final ResponseHandler responseHandler;

    @PostMapping
    @Operation(summary = "Create or update permission")
    public ResponseEntity<ApiResponse<PermissionResponse>> upsert(
            @Valid @RequestBody ApiRequest<PermissionRequest> request) {
        return responseHandler.ok(
                permissionService.upsert(request.getData()),
                "Permission saved successfully"
        );
    }

    @GetMapping
    @Operation(summary = "Get all permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> findAll() {
        return responseHandler.ok(
                permissionService.findAll(),
                "Permissions fetched successfully"
        );
    }

    @GetMapping("/active")
    @Operation(summary = "Get active permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> findActive() {
        return responseHandler.ok(
                permissionService.findActive(),
                "Active permissions fetched successfully"
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get permission by ID")
    public ResponseEntity<ApiResponse<PermissionResponse>> findById(@PathVariable UUID id) {
        return responseHandler.ok(
                permissionService.findById(id),
                "Permission fetched successfully"
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete permission")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        permissionService.delete(id);
        return responseHandler.ok(null, "Permission deleted successfully");
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Toggle permission status")
    public ResponseEntity<ApiResponse<PermissionResponse>> toggle(@PathVariable UUID id) {
        return responseHandler.ok(
                permissionService.toggleStatus(id),
                "Permission status toggled successfully"
        );
    }
}