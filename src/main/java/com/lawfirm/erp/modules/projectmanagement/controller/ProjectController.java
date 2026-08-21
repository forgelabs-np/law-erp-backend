package com.lawfirm.erp.modules.projectmanagement.controller;

import com.lawfirm.erp.common.constant.ProjectManagementConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.modules.projectmanagement.dto.request.*;
import com.lawfirm.erp.modules.projectmanagement.dto.response.*;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
import com.lawfirm.erp.modules.projectmanagement.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = ProjectManagementConstants.TAG_PROJECTS)
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = ProjectManagementConstants.CREATE_PROJECT)
    public ApiResponse<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        return ApiResponse.success("Project created", projectService.createProject(request));
    }

    @GetMapping
    @Operation(summary = ProjectManagementConstants.LIST_PROJECTS)
    public ApiResponse<Page<ProjectSummaryResponse>> listProjects(
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(projectService.listProjects(status, search, page, size));
    }

    @GetMapping("/{projectCode}")
    @Operation(summary = ProjectManagementConstants.GET_PROJECT)
    public ApiResponse<ProjectResponse> getProject(@PathVariable String projectCode) {
        return ApiResponse.success(projectService.getProject(projectCode));
    }

    @PutMapping("/{projectCode}")
    @Operation(summary = ProjectManagementConstants.UPDATE_PROJECT)
    public ApiResponse<ProjectResponse> updateProject(
            @PathVariable String projectCode,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ApiResponse.success("Project updated", projectService.updateProject(projectCode, request));
    }

    @PatchMapping("/{projectCode}/status")
    @Operation(summary = ProjectManagementConstants.UPDATE_PROJECT_STATUS)
    public ApiResponse<ProjectResponse> updateStatus(
            @PathVariable String projectCode,
            @RequestParam ProjectStatus status) {
        return ApiResponse.success("Status updated", projectService.updateProjectStatus(projectCode, status));
    }

    @PostMapping("/{projectCode}/members")
    @Operation(summary = ProjectManagementConstants.ADD_MEMBER)
    public ApiResponse<ProjectMemberResponse> addMember(
            @PathVariable String projectCode,
            @Valid @RequestBody AddMemberRequest request) {
        return ApiResponse.success("Member added", projectService.addMember(projectCode, request));
    }

    @DeleteMapping("/{projectCode}/members/{userId}")
    @Operation(summary = ProjectManagementConstants.REMOVE_MEMBER)
    public ApiResponse<Void> removeMember(
            @PathVariable String projectCode,
            @PathVariable UUID userId) {
        projectService.removeMember(projectCode, userId);
        return ApiResponse.success("Member removed", null);
    }

    @GetMapping("/{projectCode}/members")
    @Operation(summary = ProjectManagementConstants.LIST_MEMBERS)
    public ApiResponse<List<ProjectMemberResponse>> listMembers(@PathVariable String projectCode) {
        return ApiResponse.success(projectService.listMembers(projectCode));
    }
}
