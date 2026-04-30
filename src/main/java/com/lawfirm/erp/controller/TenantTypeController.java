package com.lawfirm.erp.controller;
import com.lawfirm.erp.dto.ApiRequest;
import com.lawfirm.erp.dto.ApiResponse;
import com.lawfirm.erp.dto.Tenant.request.TenantTypeRequest;
import com.lawfirm.erp.dto.Tenant.response.TenantTypeResponse;
import com.lawfirm.erp.exception.ResponseHandler;
import com.lawfirm.erp.service.TenantTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/tenant-types")
@RequiredArgsConstructor
@Tag(name = "Admin - Tenant Type Management", description = "Super Admin tenant type management APIs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantTypeController {

    private final TenantTypeService tenantTypeService;
    private final ResponseHandler responseHandler;

    @PostMapping
    @Operation(summary = "Create tenant type", description = "Create a new tenant type (SOLO, LAW_FIRM, etc.)")
    public ResponseEntity<ApiResponse<TenantTypeResponse>> createTenantType(
            @Valid @RequestBody ApiRequest<TenantTypeRequest> request,
            @RequestAttribute("userId") Long adminId) {
        return responseHandler.ok(
                tenantTypeService.createTenantType(request.getData(), adminId),
                "Tenant type created successfully"
        );
    }

    @GetMapping
    @Operation(summary = "Get all tenant types", description = "Get all tenant types")
    public ResponseEntity<ApiResponse<List<TenantTypeResponse>>> getAllTenantTypes() {
        return responseHandler.ok(
                tenantTypeService.getAllTenantTypes(),
                "Tenant types fetched successfully"
        );
    }

    @GetMapping("/active")
    @Operation(summary = "Get active tenant types", description = "Get only active tenant types")
    public ResponseEntity<ApiResponse<List<TenantTypeResponse>>> getActiveTenantTypes() {
        return responseHandler.ok(
                tenantTypeService.getActiveTenantTypes(),
                "Active tenant types fetched successfully"
        );
    }
}
