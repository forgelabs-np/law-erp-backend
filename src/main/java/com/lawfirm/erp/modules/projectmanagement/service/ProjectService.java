package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.modules.projectmanagement.dto.request.*;
import com.lawfirm.erp.modules.projectmanagement.dto.response.*;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface ProjectService {

    ProjectResponse createProject(CreateProjectRequest request);

    Page<ProjectSummaryResponse> listProjects(ProjectStatus status, String search,
                                               int page, int size);

    ProjectResponse getProject(String projectCode);

    ProjectResponse updateProject(String projectCode, UpdateProjectRequest request);

    ProjectResponse updateProjectStatus(String projectCode, ProjectStatus status);

    ProjectMemberResponse addMember(String projectCode, AddMemberRequest request);

    void removeMember(String projectCode, UUID userId);

    List<ProjectMemberResponse> listMembers(String projectCode);
}
