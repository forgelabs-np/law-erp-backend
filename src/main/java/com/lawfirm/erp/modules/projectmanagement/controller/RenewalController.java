package com.lawfirm.erp.modules.projectmanagement.controller;

import com.lawfirm.erp.common.constant.ProjectManagementConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.modules.projectmanagement.dto.request.*;
import com.lawfirm.erp.modules.projectmanagement.dto.response.*;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalStatus;
import com.lawfirm.erp.modules.projectmanagement.service.RenewalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectCode}/renewals")
@RequiredArgsConstructor
@Tag(name = ProjectManagementConstants.TAG_RENEWALS)
public class RenewalController {

    private final RenewalService renewalService;

    @PostMapping
    @Operation(summary = ProjectManagementConstants.CREATE_RENEWAL)
    public ApiResponse<RenewalResponse> createRenewal(
            @PathVariable String projectCode,
            @Valid @RequestBody CreateRenewalRequest request) {
        return ApiResponse.success("Renewal created",
                renewalService.createRenewal(projectCode, request));
    }

    @GetMapping
    @Operation(summary = ProjectManagementConstants.LIST_RENEWALS)
    public ApiResponse<List<RenewalResponse>> listRenewals(@PathVariable String projectCode) {
        return ApiResponse.success(renewalService.listRenewals(projectCode));
    }

    @GetMapping("/{id}")
    @Operation(summary = ProjectManagementConstants.GET_RENEWAL)
    public ApiResponse<RenewalResponse> getRenewal(
            @PathVariable String projectCode,
            @PathVariable Long id) {
        return ApiResponse.success(renewalService.getRenewal(projectCode, id));
    }

    @PutMapping("/{id}")
    @Operation(summary = ProjectManagementConstants.UPDATE_RENEWAL)
    public ApiResponse<RenewalResponse> updateRenewal(
            @PathVariable String projectCode,
            @PathVariable Long id,
            @Valid @RequestBody UpdateRenewalRequest request) {
        return ApiResponse.success("Renewal updated",
                renewalService.updateRenewal(projectCode, id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = ProjectManagementConstants.UPDATE_RENEWAL_STATUS)
    public ApiResponse<RenewalResponse> updateStatus(
            @PathVariable String projectCode,
            @PathVariable Long id,
            @RequestParam RenewalStatus status) {
        return ApiResponse.success("Status updated",
                renewalService.updateRenewalStatus(projectCode, id, status));
    }

    @PatchMapping("/{renewalId}/instances/{instanceId}")
    @Operation(summary = ProjectManagementConstants.UPDATE_INSTANCE_STATUS)
    public ApiResponse<RenewalInstanceResponse> updateInstanceStatus(
            @PathVariable String projectCode,
            @PathVariable Long renewalId,
            @PathVariable Long instanceId,
            @Valid @RequestBody UpdateInstanceStatusRequest request) {
        return ApiResponse.success("Instance updated",
                renewalService.updateInstanceStatus(projectCode, renewalId, instanceId, request));
    }
}
