package com.lawfirm.erp.modules.usermanagement.controller;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.constant.UserManagementConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.usermanagement.dto.response.GlobalDashboardResponse;
import com.lawfirm.erp.modules.usermanagement.service.GlobalDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/modules/dashboard")
@RequiredArgsConstructor
@Tag(name = "Global Dashboard", description = "Site-wide dashboard with stats across all modules")
public class GlobalDashboardController {

    private final GlobalDashboardService dashboardService;
    private final CurrentUserResolver currentUserResolver;
    private final ResponseHandler responseHandler;

    @GetMapping
    @Operation(summary = UserManagementConstants.GET_DASHBOARD_SUMMARY, description = UserManagementConstants.GET_DASHBOARD_DESCRIPTION)
    public ResponseEntity<ApiResponse<GlobalDashboardResponse>> getDashboard(
            @RequestParam(defaultValue = "30") int days) {
        // SUPER_ADMIN sees all firms; FIRM_ADMIN sees own firm only — enforced in service
        if (!currentUserResolver.isSuperAdmin() && currentUserResolver.getCurrentFirmId() == null) {
            throw new ForbiddenException("Firm context required");
        }
        return responseHandler.ok(dashboardService.getDashboard(days), "Dashboard fetched successfully");
    }
}
