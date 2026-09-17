package com.lawfirm.erp.modules.casemanagement.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.casemanagement.dto.response.LawyerDashboardResponse;
import com.lawfirm.erp.modules.casemanagement.service.LawyerDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lawyer")
@RequiredArgsConstructor
@Tag(name = "Lawyer Dashboard", description = "Lawyer-specific endpoints combining case management and scraper data for easier case tracking")
public class LawyerDashboardController {

    private final LawyerDashboardService lawyerDashboardService;
    private final PermissionEvaluator permissionEvaluator;
    private final ResponseHandler responseHandler;

    @GetMapping("/dashboard/{advocateId}")
    @Operation(summary = "Lawyer dashboard", 
            description = "Returns comprehensive dashboard for a lawyer: upcoming hearings, case status, deadlines, and notifications.")
    public ResponseEntity<ApiResponse<LawyerDashboardResponse>> getLawyerDashboard(
            @PathVariable UUID advocateId) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
        return responseHandler.ok(lawyerDashboardService.getLawyerDashboard(advocateId),
                "Lawyer dashboard fetched successfully");
    }

    @GetMapping("/cases/{advocateId}")
    @Operation(summary = "Lawyer's cases",
            description = "Returns all active cases assigned to a specific lawyer with current status and next hearing info.")
    public ResponseEntity<ApiResponse<LawyerDashboardResponse.CaseSummary>> getLawyerCases(
            @PathVariable UUID advocateId) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
        return responseHandler.ok(lawyerDashboardService.getLawyerCases(advocateId),
                "Lawyer cases fetched successfully");
    }

    @GetMapping("/hearings/{advocateId}")
    @Operation(summary = "Lawyer's upcoming hearings",
            description = "Returns upcoming hearings for a specific lawyer from both court registry scraper and internal court events.")
    public ResponseEntity<ApiResponse<LawyerDashboardResponse.HearingSummary>> getLawyerHearings(
            @PathVariable UUID advocateId,
            @RequestParam(defaultValue = "7") int withinDays) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
        return responseHandler.ok(lawyerDashboardService.getLawyerHearings(advocateId, withinDays),
                "Lawyer hearings fetched successfully");
    }

    @GetMapping("/deadlines/{advocateId}")
    @Operation(summary = "Lawyer's upcoming deadlines",
            description = "Returns upcoming deadlines for a specific lawyer: filing deadlines, appeal deadlines, response deadlines.")
    public ResponseEntity<ApiResponse<LawyerDashboardResponse.DeadlineSummary>> getLawyerDeadlines(
            @PathVariable UUID advocateId,
            @RequestParam(defaultValue = "30") int withinDays) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
        return responseHandler.ok(lawyerDashboardService.getLawyerDeadlines(advocateId, withinDays),
                "Lawyer deadlines fetched successfully");
    }
}
