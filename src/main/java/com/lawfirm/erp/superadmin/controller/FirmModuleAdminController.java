package com.lawfirm.erp.superadmin.controller;

import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.firm.request.EnableModuleRequest;
import com.lawfirm.erp.dto.firm.response.FirmModuleResponse;
import com.lawfirm.erp.firm.service.FirmModuleService;
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
@RequestMapping("/api/v1/super-admin/firms/{firmId}/modules")
@RequiredArgsConstructor
@Tag(name = "Super Admin - Firm Module Management", description = "Enable/disable modules for a firm")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class FirmModuleAdminController {

    private final FirmModuleService firmModuleService;
    private final ResponseHandler responseHandler;

    @PostMapping
    @Operation(summary = "Enable/disable a module for a firm")
    public ResponseEntity<ApiResponse<FirmModuleResponse>> enableModule(
            @PathVariable UUID firmId,
            @Valid @RequestBody ApiRequest<EnableModuleRequest> request) {
        return responseHandler.ok(
                firmModuleService.enableModuleForFirm(firmId, request.getData()),
                "Module configuration updated"
        );
    }

    @GetMapping
    @Operation(summary = "Get all modules with status for a firm")
    public ResponseEntity<ApiResponse<List<FirmModuleResponse>>> getFirmModules(
            @PathVariable UUID firmId) {
        return responseHandler.ok(
                firmModuleService.getFirmModules(firmId),
                "Firm modules fetched successfully"
        );
    }
}