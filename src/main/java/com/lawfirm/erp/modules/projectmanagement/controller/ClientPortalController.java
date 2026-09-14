package com.lawfirm.erp.modules.projectmanagement.controller;

import com.lawfirm.erp.common.constant.ProjectManagementConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.modules.projectmanagement.dto.response.ClientProjectResponse;
import com.lawfirm.erp.modules.projectmanagement.dto.response.RenewalInstanceResponse;
import com.lawfirm.erp.modules.projectmanagement.service.ClientPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/client/projects")
@RequiredArgsConstructor
@Tag(name = ProjectManagementConstants.TAG_CLIENT_PORTAL)
public class ClientPortalController {

    private final ClientPortalService clientPortalService;

    @GetMapping
    @Operation(summary = ProjectManagementConstants.CLIENT_LIST_PROJECTS)
    public ApiResponse<List<ClientProjectResponse>> listMyProjects() {
        return ApiResponse.success(clientPortalService.listMyProjects());
    }

    @GetMapping("/{projectCode}")
    @Operation(summary = ProjectManagementConstants.CLIENT_GET_PROJECT)
    public ApiResponse<ClientProjectResponse> getProject(@PathVariable String projectCode) {
        return ApiResponse.success(clientPortalService.getProject(projectCode));
    }

    @GetMapping("/{projectCode}/renewals")
    @Operation(summary = ProjectManagementConstants.CLIENT_LIST_RENEWALS)
    public ApiResponse<List<RenewalInstanceResponse>> listMyRenewals(
            @PathVariable String projectCode) {
        return ApiResponse.success(clientPortalService.listMyRenewals(projectCode));
    }
}
