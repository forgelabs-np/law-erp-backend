package com.lawfirm.erp.tenant.controller;

import com.lawfirm.erp.common.constant.TenantConstants;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.Tenant.request.TenantTypeRequest;
import com.lawfirm.erp.dto.Tenant.response.TenantTypeResponse;
import com.lawfirm.erp.tenant.service.TenantTypeService;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
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
@RequestMapping("/api/v1/admin/tenant-types")
@RequiredArgsConstructor
@Tag(name = "Admin - Tenant Type Management", description = "Super Admin tenant type management APIs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantTypeController {

    private final TenantTypeService tenantTypeService;
    private final ResponseHandler responseHandler;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping
    @Operation(summary = TenantConstants.CREATE_TENANT_TYPE_SUMMARY, description = TenantConstants.CREATE_TENANT_TYPE_DESCRIPTION)
    public ResponseEntity<ApiResponse<TenantTypeResponse>> createTenantType(
            @Valid @RequestBody ApiRequest<TenantTypeRequest> request) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        return responseHandler.ok(
                tenantTypeService.createTenantType(request.getData(), adminId),
                "Tenant type created successfully"
        );
    }

    @GetMapping
    @Operation(summary = TenantConstants.GET_ALL_TENANT_TYPES_SUMMARY, description = TenantConstants.GET_ALL_TENANT_TYPES_DESCRIPTION)
    public ResponseEntity<ApiResponse<List<TenantTypeResponse>>> getAllTenantTypes() {
        return responseHandler.ok(
                tenantTypeService.getAllTenantTypes(),
                "Tenant types fetched successfully"
        );
    }

    @GetMapping("/active")
    @Operation(summary = TenantConstants.GET_ACTIVE_TENANT_TYPES_SUMMARY, description = TenantConstants.GET_ACTIVE_TENANT_TYPES_DESCRIPTION)
    public ResponseEntity<ApiResponse<List<TenantTypeResponse>>> getActiveTenantTypes() {
        return responseHandler.ok(
                tenantTypeService.getActiveTenantTypes(),
                "Active tenant types fetched successfully"
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = TenantConstants.GET_TENANT_TYPE_BY_ID_SUMMARY)
    public ResponseEntity<ApiResponse<TenantTypeResponse>> getTenantTypeById(@PathVariable UUID id) {
        return responseHandler.ok(
                tenantTypeService.getTenantTypeById(id),
                "Tenant type fetched successfully"
        );
    }

    @PutMapping("/{id}")
    @Operation(summary = TenantConstants.UPDATE_TENANT_TYPE_SUMMARY)
    public ResponseEntity<ApiResponse<TenantTypeResponse>> updateTenantType(
            @PathVariable UUID id,
            @Valid @RequestBody ApiRequest<TenantTypeRequest> request) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        return responseHandler.ok(
                tenantTypeService.updateTenantType(id, request.getData(), adminId),
                "Tenant type updated successfully"
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = TenantConstants.DELETE_TENANT_TYPE_SUMMARY)
    public ResponseEntity<ApiResponse<Void>> deleteTenantType(@PathVariable UUID id) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        tenantTypeService.deleteTenantType(id, adminId);
        return responseHandler.ok(null, "Tenant type deleted successfully");
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = TenantConstants.TOGGLE_TENANT_TYPE_SUMMARY)
    public ResponseEntity<ApiResponse<TenantTypeResponse>> toggleTenantType(@PathVariable UUID id) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        return responseHandler.ok(
                tenantTypeService.toggleTenantTypeStatus(id, adminId),
                "Tenant type status toggled successfully"
        );
    }
}
