package com.lawfirm.erp.modules.projectmanagement.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.constant.ProjectManagementConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.modules.projectmanagement.dto.response.ProjectDashboardResponse;
import com.lawfirm.erp.modules.projectmanagement.service.ProjectDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/dashboard")
@RequiredArgsConstructor
@Tag(name = ProjectManagementConstants.TAG_DASHBOARD)
public class ProjectDashboardController {

    private final ProjectDashboardService dashboardService;
    private final PermissionEvaluator permissionEvaluator;

    @GetMapping
    @Operation(summary = ProjectManagementConstants.PROJECT_DASHBOARD)
    public ApiResponse<ProjectDashboardResponse> getDashboard() {
        permissionEvaluator.require("PROJECT_MANAGEMENT:VIEW");
        return ApiResponse.success(dashboardService.getDashboard());
    }
}
