package com.lawfirm.erp.modules.usermanagement.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.usermanagement.dashboard.*;
import com.lawfirm.erp.modules.usermanagement.dto.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "User-type-specific dashboards — one endpoint per role")
public class UserDashboardController {

    private final DashboardScopeFactory scopeFactory;
    private final SuperAdminDashboardService superAdminDashboardService;
    private final FirmDashboardService firmDashboardService;
    private final EmployeeDashboardService employeeDashboardService;
    private final ClientDashboardService clientDashboardService;
    private final PermissionEvaluator permissionEvaluator;
    private final ResponseHandler responseHandler;

    @GetMapping("/super-admin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Platform-wide dashboard for Super Admin",
            description = "Firms, users, cases, scraper health, trial alerts, trends across all tenants")
    public ResponseEntity<ApiResponse<SuperAdminDashboardResponse>> superAdminDashboard() {
        permissionEvaluator.require("DASHBOARD_MANAGEMENT:VIEW");
        DashboardScope scope = scopeFactory.build();
        return responseHandler.ok(
                superAdminDashboardService.getDashboard(scope),
                "Super Admin dashboard fetched"
        );
    }

    @GetMapping("/firm")
    @PreAuthorize("hasRole('FIRM_ADMIN')")
    @Operation(summary = "Firm dashboard for Firm Admin",
            description = "Cases, hearings, invoices, renewals, team caseload — firm-scoped")
    public ResponseEntity<ApiResponse<FirmDashboardResponse>> firmDashboard() {
        permissionEvaluator.require("DASHBOARD_MANAGEMENT:VIEW");
        DashboardScope scope = scopeFactory.build();
        if (scope.getFirmId() == null) throw new ForbiddenException("Firm context required");
        return responseHandler.ok(
                firmDashboardService.getDashboard(scope),
                "Firm dashboard fetched"
        );
    }

    @GetMapping("/employee")
    @PreAuthorize("hasAnyRole('ADVOCATE','PARALEGAL')")
    @Operation(summary = "Employee dashboard for Advocate/Paralegal",
            description = "My assigned cases, today's events, upcoming hearings, deadlines, stale matters")
    public ResponseEntity<ApiResponse<EmployeeDashboardResponse>> employeeDashboard() {
        permissionEvaluator.require("DASHBOARD_MANAGEMENT:VIEW");
        DashboardScope scope = scopeFactory.build();
        if (scope.getFirmId() == null) throw new ForbiddenException("Firm context required");
        return responseHandler.ok(
                employeeDashboardService.getDashboard(scope),
                "Employee dashboard fetched"
        );
    }

    @GetMapping("/client")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Client dashboard",
            description = "My matters, next hearing, upcoming events, invoices")
    public ResponseEntity<ApiResponse<ClientDashboardResponse>> clientDashboard() {
        permissionEvaluator.require("DASHBOARD_MANAGEMENT:VIEW");
        DashboardScope scope = scopeFactory.build();
        if (scope.getFirmId() == null) throw new ForbiddenException("Firm context required");
        return responseHandler.ok(
                clientDashboardService.getDashboard(scope),
                "Client dashboard fetched"
        );
    }
}
