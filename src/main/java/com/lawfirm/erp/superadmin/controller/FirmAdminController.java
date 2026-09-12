package com.lawfirm.erp.superadmin.controller;

import com.lawfirm.erp.common.constant.FirmConstants;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.firm.request.CreateFirmRequest;
import com.lawfirm.erp.dto.firm.request.ExtendTrialRequest;
import com.lawfirm.erp.dto.firm.response.FirmAdminResponse;
import com.lawfirm.erp.dto.firm.response.FirmCreationResponse;
import com.lawfirm.erp.firm.service.FirmAdminService;
import com.lawfirm.erp.firm.service.FirmService;
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
@RequestMapping("/api/v1/super-admin/firms")
@RequiredArgsConstructor
@Tag(name = "Super Admin - Firm Management", description = "Super Admin firm management APIs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class FirmAdminController {

    private final FirmService firmService;
    private final FirmAdminService firmAdminService;
    private final ResponseHandler responseHandler;

    @PostMapping
    @Operation(summary = FirmConstants.CREATE_FIRM_SUMMARY)
    public ResponseEntity<ApiResponse<FirmCreationResponse>> createFirm(
            @Valid @RequestBody ApiRequest<CreateFirmRequest> request) {
        return responseHandler.ok(
                firmService.createFirm(request.getData()),
                "Firm created successfully"
        );
    }

    @GetMapping("/admins")
    @Operation(summary = FirmConstants.GET_ALL_FIRM_ADMINS_SUMMARY)
    public ResponseEntity<ApiResponse<List<FirmAdminResponse>>> getAllFirmAdmins() {
        return responseHandler.ok(
                firmAdminService.getAllFirmAdmins(),
                "Firm admins fetched successfully"
        );
    }

    @GetMapping("/{firmId}/admins")
    @Operation(summary = FirmConstants.GET_FIRM_ADMINS_BY_FIRM_SUMMARY)
    public ResponseEntity<ApiResponse<List<FirmAdminResponse>>> getFirmAdminsByFirmId(
            @PathVariable UUID firmId) {
        return responseHandler.ok(
                firmAdminService.getFirmAdminsByFirmId(firmId),
                "Firm admins fetched successfully"
        );
    }

    @GetMapping("/admins/{adminId}")
    @Operation(summary = FirmConstants.GET_FIRM_ADMIN_BY_ID_SUMMARY)
    public ResponseEntity<ApiResponse<FirmAdminResponse>> getFirmAdminById(
            @PathVariable UUID adminId) {
        return responseHandler.ok(
                firmAdminService.getFirmAdminById(adminId),
                "Firm admin fetched successfully"
        );
    }

    @PatchMapping("/admins/{adminId}/toggle")
    @Operation(summary = FirmConstants.TOGGLE_FIRM_ADMIN_STATUS_SUMMARY)
    public ResponseEntity<ApiResponse<FirmAdminResponse>> toggleFirmAdminStatus(
            @PathVariable UUID adminId) {
        return responseHandler.ok(
                firmAdminService.toggleFirmAdminStatus(adminId),
                "Firm admin status toggled successfully"
        );
    }

    @PutMapping("/{firmId}/suspend")
    @Operation(summary = FirmConstants.SUSPEND_FIRM_SUMMARY)
    public ResponseEntity<ApiResponse<Void>> suspendFirm(@PathVariable UUID firmId) {
        firmService.suspendFirm(firmId);
        return responseHandler.ok(null, "Firm suspended successfully");
    }

    @PutMapping("/{firmId}/activate")
    @Operation(summary = FirmConstants.ACTIVATE_FIRM_SUMMARY)
    public ResponseEntity<ApiResponse<Void>> activateFirm(@PathVariable UUID firmId) {
        firmService.activateFirm(firmId);
        return responseHandler.ok(null, "Firm activated successfully");
    }

    @PutMapping("/{firmId}/extend-trial")
    @Operation(summary = FirmConstants.EXTEND_TRIAL_SUMMARY)
    public ResponseEntity<ApiResponse<Void>> extendTrial(
            @PathVariable UUID firmId,
            @Valid @RequestBody ApiRequest<ExtendTrialRequest> request) {
        firmService.extendTrial(firmId, request.getData().getAdditionalDays());
        return responseHandler.ok(null, "Trial extended successfully");
    }

    @PutMapping("/{firmId}/convert-to-permanent")
    @Operation(summary = FirmConstants.CONVERT_TO_PERMANENT_SUMMARY)
    public ResponseEntity<ApiResponse<Void>> convertToPermanent(@PathVariable UUID firmId) {
        firmService.convertToPermanent(firmId);
        return responseHandler.ok(null, "Firm converted to permanent successfully");
    }
}