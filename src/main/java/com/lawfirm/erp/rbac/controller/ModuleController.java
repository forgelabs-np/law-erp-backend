package com.lawfirm.erp.rbac.controller;

import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.admin.request.AssignPermissionsRequest;
import com.lawfirm.erp.dto.admin.request.ModuleRequest;
import com.lawfirm.erp.dto.admin.response.ModuleResponse;
import com.lawfirm.erp.rbac.service.ModuleService;
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
@RequestMapping("/api/v1/admin/modules")
@RequiredArgsConstructor
@Tag(name = "Admin - Module Management", description = "Super Admin module management APIs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ModuleController {

    private final ModuleService moduleService;
    private final ResponseHandler responseHandler;

    @PostMapping
    @Operation(summary = "Create or update module")
    public ResponseEntity<ApiResponse<ModuleResponse>> upsertModule(
            @Valid @RequestBody ApiRequest<ModuleRequest> request) {
        return responseHandler.ok(
                moduleService.upsertModule(request.getData()),
                "Module saved successfully"
        );
    }

    @GetMapping
    @Operation(summary = "Get all modules")
    public ResponseEntity<ApiResponse<List<ModuleResponse>>> getAllModules() {
        return responseHandler.ok(
                moduleService.getAllModules(),
                "Modules fetched successfully"
        );
    }

    @GetMapping("/active")
    @Operation(summary = "Get active modules")
    public ResponseEntity<ApiResponse<List<ModuleResponse>>> getActiveModules() {
        return responseHandler.ok(
                moduleService.getActiveModules(),
                "Active modules fetched successfully"
        );
    }

    @GetMapping("/{moduleId}")
    @Operation(summary = "Get module by ID")
    public ResponseEntity<ApiResponse<ModuleResponse>> getModuleById(@PathVariable UUID moduleId) {
        return responseHandler.ok(
                moduleService.getModuleById(moduleId),
                "Module fetched successfully"
        );
    }

    @DeleteMapping("/{moduleId}")
    @Operation(summary = "Delete module")
    public ResponseEntity<ApiResponse<Void>> deleteModule(@PathVariable UUID moduleId) {
        moduleService.deleteModule(moduleId);
        return responseHandler.ok(null, "Module deleted successfully");
    }

    @PatchMapping("/{moduleId}/toggle")
    @Operation(summary = "Toggle module status")
    public ResponseEntity<ApiResponse<ModuleResponse>> toggleModuleStatus(@PathVariable UUID moduleId) {
        return responseHandler.ok(
                moduleService.toggleModuleStatus(moduleId),
                "Module status toggled successfully"
        );
    }

    @PostMapping("/{moduleId}/permissions")
    @Operation(summary = "Assign permissions to module")
    public ResponseEntity<ApiResponse<ModuleResponse>> assignPermissionsToModule(
            @PathVariable UUID moduleId,
            @Valid @RequestBody ApiRequest<AssignPermissionsRequest> request) {
        return responseHandler.ok(
                moduleService.assignPermissionsToModule(moduleId, request.getData().getPermissionIds()),
                "Permissions assigned to module successfully"
        );
    }
}