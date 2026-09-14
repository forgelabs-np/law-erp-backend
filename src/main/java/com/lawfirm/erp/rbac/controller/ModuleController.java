package com.lawfirm.erp.rbac.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.constant.RbacConstants;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/modules")
@RequiredArgsConstructor
@Tag(name = "Admin - Module Management", description = "Super Admin module management APIs")
public class ModuleController {

    private final ModuleService moduleService;
    private final PermissionEvaluator permissionEvaluator;
    private final ResponseHandler responseHandler;

    @PostMapping
    @Operation(summary = RbacConstants.UPSERT_MODULE_SUMMARY)
    public ResponseEntity<ApiResponse<ModuleResponse>> upsertModule(
            @Valid @RequestBody ApiRequest<ModuleRequest> request) {
        permissionEvaluator.require("MENU_MANAGEMENT:CREATE");
        return responseHandler.ok(
                moduleService.upsertModule(request.getData()),
                "Module saved successfully"
        );
    }

    @GetMapping
    @Operation(summary = RbacConstants.GET_ALL_MODULES_SUMMARY)
    public ResponseEntity<ApiResponse<List<ModuleResponse>>> getAllModules() {
        permissionEvaluator.require("MENU_MANAGEMENT:VIEW");
        return responseHandler.ok(
                moduleService.getAllModules(),
                "Modules fetched successfully"
        );
    }

    @GetMapping("/active")
    @Operation(summary = RbacConstants.GET_ACTIVE_MODULES_SUMMARY)
    public ResponseEntity<ApiResponse<List<ModuleResponse>>> getActiveModules() {
        permissionEvaluator.require("MENU_MANAGEMENT:VIEW");
        return responseHandler.ok(
                moduleService.getActiveModules(),
                "Active modules fetched successfully"
        );
    }

    @GetMapping("/{moduleId}")
    @Operation(summary = RbacConstants.GET_MODULE_BY_ID_SUMMARY)
    public ResponseEntity<ApiResponse<ModuleResponse>> getModuleById(@PathVariable UUID moduleId) {
        permissionEvaluator.require("MENU_MANAGEMENT:VIEW");
        return responseHandler.ok(
                moduleService.getModuleById(moduleId),
                "Module fetched successfully"
        );
    }

    @DeleteMapping("/{moduleId}")
    @Operation(summary = RbacConstants.DELETE_MODULE_SUMMARY)
    public ResponseEntity<ApiResponse<Void>> deleteModule(@PathVariable UUID moduleId) {
        permissionEvaluator.require("MENU_MANAGEMENT:DELETE");
        moduleService.deleteModule(moduleId);
        return responseHandler.ok(null, "Module deleted successfully");
    }

    @PatchMapping("/{moduleId}/toggle")
    @Operation(summary = RbacConstants.TOGGLE_MODULE_SUMMARY)
    public ResponseEntity<ApiResponse<ModuleResponse>> toggleModuleStatus(@PathVariable UUID moduleId) {
        permissionEvaluator.require("MENU_MANAGEMENT:EDIT");
        return responseHandler.ok(
                moduleService.toggleModuleStatus(moduleId),
                "Module status toggled successfully"
        );
    }

    @PostMapping("/{moduleId}/permissions")
    @Operation(summary = RbacConstants.ASSIGN_PERMISSIONS_TO_MODULE_SUMMARY)
    public ResponseEntity<ApiResponse<ModuleResponse>> assignPermissionsToModule(
            @PathVariable UUID moduleId,
            @Valid @RequestBody ApiRequest<AssignPermissionsRequest> request) {
        permissionEvaluator.require("MENU_MANAGEMENT:EDIT");
        return responseHandler.ok(
                moduleService.assignPermissionsToModule(moduleId, request.getData().getPermissionIds()),
                "Permissions assigned to module successfully"
        );
    }
}