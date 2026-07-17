package com.lawfirm.erp.firm.controller;

import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.response.RolePermissionResponse;
import com.lawfirm.erp.dto.admin.response.RoleResponse;
import com.lawfirm.erp.dto.firm.response.FirmRolePermissionsResponse;
import com.lawfirm.erp.firm.service.FirmRoleService;
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
@RequestMapping("/api/v1/firm/roles")
@RequiredArgsConstructor
@Tag(name = "Firm Role Management", description = "Firm admin role management")
@PreAuthorize("hasRole('FIRM_ADMIN')")
public class FirmRoleController {

    private final FirmRoleService firmRoleService;
    private final ResponseHandler responseHandler;


    @GetMapping
    @Operation(
            summary = "Get all roles for this firm",
            description = "Returns firm-scoped roles only. System templates are excluded. " +
                    "These are the roles firm admin can assign to employees."
    )
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getFirmRoles() {
        return responseHandler.ok(
                firmRoleService.getFirmRoles(),
                "Firm roles fetched successfully"
        );
    }

    @GetMapping("/{roleId}/permissions")
    @Operation(
            summary = "Get permissions for a firm role",
            description = "Returns two lists: " +
                    "(1) currentPermissions — what this role currently has. " +
                    "(2) availablePermissions — everything the system ceiling allows, " +
                    "with 'assigned' flag showing which are active. " +
                    "Use availablePermissions to build the checkbox UI for editing."
    )
    public ResponseEntity<ApiResponse<FirmRolePermissionsResponse>> getRolePermissions(
            @PathVariable UUID roleId) {
        return responseHandler.ok(
                firmRoleService.getRolePermissions(roleId),
                "Role permissions fetched successfully"
        );
    }

    @PutMapping("/{roleId}/permissions")
    @Operation(
            summary = "Update permissions for a firm role",
            description = "Replaces all permissions on a firm-scoped role. " +
                    "Ceiling enforced — cannot assign permissions beyond what the system role allows. " +
                    "All users holding this role will have their JWT invalidated immediately " +
                    "and must re-login to get the updated permissions."
    )
    public ResponseEntity<ApiResponse<RolePermissionResponse>> updateRolePermissions(
            @PathVariable UUID roleId,
            @Valid @RequestBody ApiRequest<RolePermissionRequest> request) {

        request.getData().setRoleId(roleId);

        return responseHandler.ok(
                firmRoleService.updateRolePermissions(roleId, request.getData()),
                "Role permissions updated. Affected users must re-login."
        );
    }
}