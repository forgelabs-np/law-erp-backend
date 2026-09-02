package com.lawfirm.erp.modules.casemanagement.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.constant.CaseManagementConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.casemanagement.dto.response.DashboardResponse;
import com.lawfirm.erp.modules.casemanagement.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/firm/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Case management dashboard with role-based stats")
public class DashboardController {

    private final DashboardService dashboardService;
    private final PermissionEvaluator permissionEvaluator;
    private final ResponseHandler responseHandler;

    @GetMapping
    @Operation(summary = CaseManagementConstants.GET_DASHBOARD_SUMMARY,
            description = CaseManagementConstants.GET_DASHBOARD_DESCRIPTION)
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        permissionEvaluator.require("DASHBOARD_MANAGEMENT:VIEW");
        return responseHandler.ok(dashboardService.getDashboard(), "Dashboard fetched successfully");
    }
}
