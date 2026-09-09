package com.lawfirm.erp.rbac.controller;

import com.lawfirm.erp.common.constant.RbacConstants;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.request.TemplatePermissionRequest;
import com.lawfirm.erp.dto.admin.request.RoleRequest;
import com.lawfirm.erp.dto.admin.response.RolePermissionResponse;
import com.lawfirm.erp.dto.admin.response.SyncJobStatusResponse;
import com.lawfirm.erp.dto.admin.response.TemplatePermissionResponse;
import com.lawfirm.erp.dto.admin.response.TemplateSyncPreviewResponse;
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
    private final com.lawfirm.erp.rbac.service.TemplatePermissionService templatePermissionService;
    private final ResponseHandler responseHandler;

    @PostMapping
    @Operation(summary = RbacConstants.UPSERT_ROLE_SUMMARY)
    public ResponseEntity<ApiResponse<RoleResponse>> upsertRole(
            @Valid @RequestBody ApiRequest<RoleRequest> request) {
        return responseHandler.ok(
                roleManagementService.upsertRole(request.getData()),
                "Role saved successfully"
        );
    }

    @GetMapping
    @Operation(summary = RbacConstants.GET_ALL_ROLES_SUMMARY)
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        return responseHandler.ok(
                roleManagementService.getAllRoles(),
                "Roles fetched successfully"
        );
    }

    @GetMapping("/active")
    @Operation(summary = RbacConstants.GET_ACTIVE_ROLES_SUMMARY)
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getActiveRoles() {
        return responseHandler.ok(
                roleManagementService.getActiveRoles(),
                "Active roles fetched successfully"
        );
    }

    @GetMapping("/templates")
    @Operation(summary = RbacConstants.GET_TEMPLATES_SUMMARY, description = RbacConstants.GET_TEMPLATES_DESCRIPTION)
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getSystemTemplates() {
        return responseHandler.ok(
                roleManagementService.getSystemTemplates(),
                "System role templates fetched successfully"
        );
    }

    @GetMapping("/{roleId}")
    @Operation(summary = RbacConstants.GET_ROLE_BY_ID_SUMMARY)
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable UUID roleId) {
        return responseHandler.ok(
                roleManagementService.getRoleById(roleId),
                "Role fetched successfully"
        );
    }

    @DeleteMapping("/{roleId}")
    @Operation(summary = RbacConstants.DELETE_ROLE_SUMMARY)
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable UUID roleId) {
        roleManagementService.deleteRole(roleId);
        return responseHandler.ok(null, "Role deleted successfully");
    }

    @PatchMapping("/{roleId}/toggle")
    @Operation(summary = RbacConstants.TOGGLE_ROLE_SUMMARY)
    public ResponseEntity<ApiResponse<RoleResponse>> toggleRoleStatus(@PathVariable UUID roleId) {
        return responseHandler.ok(
                roleManagementService.toggleRoleStatus(roleId),
                "Role status toggled successfully"
        );
    }

    @PostMapping("/permissions")
    @Operation(summary = RbacConstants.ASSIGN_PERMISSIONS_TO_ROLE_SUMMARY)
    public ResponseEntity<ApiResponse<Void>> assignPermissionsToRole(
            @Valid @RequestBody ApiRequest<RolePermissionRequest> request) {
        rolePermissionService.assignPermissionsToRole(request.getData());
        return responseHandler.ok(null, "Permissions assigned to role successfully");
    }

    @GetMapping("/templates/{templateId}/permissions")
    @Operation(summary = RbacConstants.GET_TEMPLATE_PERMISSIONS_SUMMARY)
    public ResponseEntity<ApiResponse<TemplatePermissionResponse>> getTemplatePermissions(
            @PathVariable UUID templateId) {
        return responseHandler.ok(
                templatePermissionService.getTemplatePermissions(templateId),
                "Template permissions fetched successfully"
        );
    }

    @PostMapping("/templates/{templateId}/permissions/preview")
    @Operation(summary = RbacConstants.PREVIEW_TEMPLATE_CHANGE_SUMMARY,
               description = RbacConstants.PREVIEW_TEMPLATE_CHANGE_DESCRIPTION)
    public ResponseEntity<ApiResponse<TemplateSyncPreviewResponse>> previewTemplateChange(
            @PathVariable UUID templateId,
            @Valid @RequestBody ApiRequest<TemplatePermissionRequest> request) {
        return responseHandler.ok(
                templatePermissionService.previewTemplateChange(templateId, request.getData()),
                "Template change preview generated"
        );
    }

    @PutMapping("/templates/{templateId}/permissions")
    @Operation(summary = RbacConstants.UPDATE_TEMPLATE_PERMISSIONS_SUMMARY,
               description = RbacConstants.UPDATE_TEMPLATE_PERMISSIONS_DESCRIPTION)
    public ResponseEntity<ApiResponse<TemplatePermissionResponse>> updateTemplatePermissions(
            @PathVariable UUID templateId,
            @Valid @RequestBody ApiRequest<TemplatePermissionRequest> request) {
        return responseHandler.ok(
                templatePermissionService.updateTemplatePermissions(templateId, request.getData()),
                "Template permissions updated — sync fan-out enqueued"
        );
    }

    @GetMapping("/sync-jobs/{jobId}")
    @Operation(summary = RbacConstants.GET_SYNC_JOB_STATUS_SUMMARY,
               description = RbacConstants.GET_SYNC_JOB_STATUS_DESCRIPTION)
    public ResponseEntity<ApiResponse<SyncJobStatusResponse>> getSyncJobStatus(
            @PathVariable UUID jobId) {
        return responseHandler.ok(
                templatePermissionService.getSyncJobStatus(jobId),
                "Sync job status fetched successfully"
        );
    }

    @GetMapping("/{roleId}/permissions")
    @Operation(summary = RbacConstants.GET_ROLE_PERMISSIONS_SUMMARY)
    public ResponseEntity<ApiResponse<RolePermissionResponse>> getRolePermissions(@PathVariable UUID roleId) {
        return responseHandler.ok(
                rolePermissionService.getRolePermissions(roleId),
                "Role permissions fetched successfully"
        );
    }
}