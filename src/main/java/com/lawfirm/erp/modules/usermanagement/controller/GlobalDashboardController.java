package com.lawfirm.erp.modules.usermanagement.controller;

import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.usermanagement.dto.response.GlobalDashboardResponse;
import com.lawfirm.erp.modules.usermanagement.service.GlobalDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/modules/dashboard")
@RequiredArgsConstructor
@Tag(name = "Global Dashboard", description = "Site-wide dashboard with stats across all modules")
@PreAuthorize("hasAnyRole('FIRM_ADMIN', 'SUPER_ADMIN')")
public class GlobalDashboardController {

    private final GlobalDashboardService dashboardService;
    private final ResponseHandler responseHandler;

    @GetMapping
    @Operation(summary = "Get global dashboard",
            description = "Aggregated stats across users, firms, case management, scraper, and recent activity. "
                    + "FIRM_ADMIN sees firm-scoped data; SUPER_ADMIN sees all firms.")
    public ResponseEntity<ApiResponse<GlobalDashboardResponse>> getDashboard() {
        return responseHandler.ok(dashboardService.getDashboard(), "Dashboard fetched successfully");
    }
}
