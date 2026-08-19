package com.lawfirm.erp.modules.casemanagement.controller;

import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.casemanagement.dto.response.DashboardResponse;
import com.lawfirm.erp.modules.casemanagement.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/firm/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Case management dashboard with role-based stats")
@PreAuthorize("hasAnyRole('FIRM_ADMIN', 'ADVOCATE', 'PARALEGAL')")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ResponseHandler responseHandler;

    @GetMapping
    @Operation(summary = "Get dashboard",
            description = "Firm-wide stats for FIRM_ADMIN; assigned-matters-only for ADVOCATE/PARALEGAL. "
                    + "Includes total/active/stale counts, today's events, and case positioning summaries.")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        return responseHandler.ok(dashboardService.getDashboard(), "Dashboard fetched successfully");
    }
}
