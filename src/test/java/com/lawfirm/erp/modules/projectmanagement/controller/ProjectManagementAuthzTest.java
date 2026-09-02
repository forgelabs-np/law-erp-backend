package com.lawfirm.erp.modules.projectmanagement.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.modules.projectmanagement.dto.request.AddCredentialRequest;
import com.lawfirm.erp.modules.projectmanagement.dto.request.AddMemberRequest;
import com.lawfirm.erp.modules.projectmanagement.dto.request.CreateProjectRequest;
import com.lawfirm.erp.modules.projectmanagement.dto.request.CreateRenewalRequest;
import com.lawfirm.erp.modules.projectmanagement.dto.request.CreateRenewalTypeRequest;
import com.lawfirm.erp.modules.projectmanagement.dto.request.UpdateCredentialRequest;
import com.lawfirm.erp.modules.projectmanagement.dto.request.UpdateInstanceStatusRequest;
import com.lawfirm.erp.modules.projectmanagement.dto.request.UpdateProjectRequest;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectMemberRole;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalRecurrence;
import com.lawfirm.erp.modules.projectmanagement.service.CredentialService;
import com.lawfirm.erp.modules.projectmanagement.service.ProjectDashboardService;
import com.lawfirm.erp.modules.projectmanagement.service.ProjectService;
import com.lawfirm.erp.modules.projectmanagement.service.RenewalService;
import com.lawfirm.erp.modules.projectmanagement.service.RenewalTypeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Locks the permission enforcement on the Project Management module.
 * Every endpoint must gate on the correct PROJECT_MANAGEMENT:* permission,
 * and a missing permission must propagate as ForbiddenException without
 * reaching the service layer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectManagementAuthzTest {

    private static final String CODE = "E2EFIRM-PRJ-2026-00001";

    @Mock private ProjectService projectService;
    @Mock private CredentialService credentialService;
    @Mock private RenewalService renewalService;
    @Mock private RenewalTypeService renewalTypeService;
    @Mock private ProjectDashboardService dashboardService;
    @Mock private PermissionEvaluator permissionEvaluator;

    @InjectMocks private ProjectController projectController;
    @InjectMocks private CredentialController credentialController;
    @InjectMocks private RenewalController renewalController;
    @InjectMocks private RenewalTypeController renewalTypeController;
    @InjectMocks private ProjectDashboardController projectDashboardController;

    // ── ProjectController ───────────────────────────────────────────────────

    @Test
    @DisplayName("Project endpoints require correct PROJECT_MANAGEMENT permissions")
    void projectEndpointsRequirePermissions() {
        projectController.createProject(new CreateProjectRequest());
        projectController.listProjects(null, null, 0, 20);
        projectController.getProject(CODE);
        projectController.updateProject(CODE, new UpdateProjectRequest());
        projectController.updateStatus(CODE, ProjectStatus.ACTIVE);
        AddMemberRequest member = new AddMemberRequest();
        member.setUserId(UUID.randomUUID());
        member.setRole(ProjectMemberRole.MEMBER);
        projectController.addMember(CODE, member);
        projectController.removeMember(CODE, UUID.randomUUID());
        projectController.listMembers(CODE);

        verify(permissionEvaluator).require("PROJECT_MANAGEMENT:CREATE");
        verify(permissionEvaluator, org.mockito.Mockito.times(3)).require("PROJECT_MANAGEMENT:VIEW");
        verify(permissionEvaluator, org.mockito.Mockito.times(4)).require("PROJECT_MANAGEMENT:EDIT");
    }

    @Test
    @DisplayName("Create project without permission propagates ForbiddenException and never hits service")
    void createProjectDenied() {
        doThrow(new ForbiddenException("Missing required permission: PROJECT_MANAGEMENT:CREATE"))
                .when(permissionEvaluator).require(anyString());

        assertThrows(ForbiddenException.class,
                () -> projectController.createProject(new CreateProjectRequest()));
        verify(projectService, never()).createProject(any());
    }

    // ── CredentialController ────────────────────────────────────────────────

    @Test
    @DisplayName("Credential endpoints require CREDENTIAL_VIEW/REVEAL/EDIT/DELETE")
    void credentialEndpointsRequirePermissions() {
        org.mockito.Mockito.when(credentialService.revealPassword(anyString(), any()))
                .thenReturn("decrypted-secret");
        credentialController.addCredential(CODE, new AddCredentialRequest());
        credentialController.listCredentials(CODE);
        credentialController.getCredential(CODE, 1L);
        credentialController.updateCredential(CODE, 1L, new UpdateCredentialRequest());
        credentialController.deleteCredential(CODE, 1L);
        credentialController.revealPassword(CODE, 1L);

        verify(permissionEvaluator, org.mockito.Mockito.times(2)).require("PROJECT_MANAGEMENT:EDIT");
        verify(permissionEvaluator, org.mockito.Mockito.times(2)).require("PROJECT_MANAGEMENT:CREDENTIAL_VIEW");
        verify(permissionEvaluator).require("PROJECT_MANAGEMENT:DELETE");
        verify(permissionEvaluator).require("PROJECT_MANAGEMENT:CREDENTIAL_REVEAL");
    }

    @Test
    @DisplayName("Reveal password without CREDENTIAL_REVEAL is forbidden and never decrypts")
    void revealDenied() {
        doThrow(new ForbiddenException("Missing required permission: PROJECT_MANAGEMENT:CREDENTIAL_REVEAL"))
                .when(permissionEvaluator).require("PROJECT_MANAGEMENT:CREDENTIAL_REVEAL");

        assertThrows(ForbiddenException.class,
                () -> credentialController.revealPassword(CODE, 1L));
        verify(credentialService, never()).revealPassword(anyString(), any());
    }

    // ── RenewalController ───────────────────────────────────────────────────

    @Test
    @DisplayName("Renewal endpoints require CREATE/VIEW/EDIT")
    void renewalEndpointsRequirePermissions() {
        CreateRenewalRequest create = new CreateRenewalRequest();
        create.setRenewalTypeId(1L);
        create.setTitle("T");
        create.setRecurrence(RenewalRecurrence.YEARLY);
        create.setStartDate(LocalDate.now());
        renewalController.createRenewal(CODE, create);
        renewalController.listRenewals(CODE);
        renewalController.getRenewal(CODE, 1L);

        UpdateInstanceStatusRequest instance = new UpdateInstanceStatusRequest();
        instance.setStatus(RenewalInstanceStatus.COMPLETED);
        renewalController.updateInstanceStatus(CODE, 1L, 1L, instance);

        verify(permissionEvaluator).require("PROJECT_MANAGEMENT:CREATE");
        verify(permissionEvaluator, org.mockito.Mockito.times(2)).require("PROJECT_MANAGEMENT:VIEW");
        verify(permissionEvaluator).require("PROJECT_MANAGEMENT:EDIT");
    }

    @Test
    @DisplayName("Create renewal without permission is forbidden")
    void createRenewalDenied() {
        doThrow(new ForbiddenException("Missing required permission: PROJECT_MANAGEMENT:CREATE"))
                .when(permissionEvaluator).require(anyString());

        CreateRenewalRequest create = new CreateRenewalRequest();
        create.setRenewalTypeId(1L);
        create.setTitle("T");
        create.setRecurrence(RenewalRecurrence.YEARLY);
        create.setStartDate(LocalDate.now());

        assertThrows(ForbiddenException.class,
                () -> renewalController.createRenewal(CODE, create));
        verify(renewalService, never()).createRenewal(anyString(), any());
    }

    // ── RenewalTypeController ───────────────────────────────────────────────

    @Test
    @DisplayName("Renewal type endpoints require VIEW/CREATE/EDIT/DELETE")
    void renewalTypeEndpointsRequirePermissions() {
        renewalTypeController.listTypes();
        renewalTypeController.createType(new CreateRenewalTypeRequest());
        renewalTypeController.updateType(1L, new CreateRenewalTypeRequest());
        renewalTypeController.deleteType(1L);

        verify(permissionEvaluator).require("PROJECT_MANAGEMENT:VIEW");
        verify(permissionEvaluator).require("PROJECT_MANAGEMENT:CREATE");
        verify(permissionEvaluator).require("PROJECT_MANAGEMENT:EDIT");
        verify(permissionEvaluator).require("PROJECT_MANAGEMENT:DELETE");
    }

    // ── ProjectDashboardController ──────────────────────────────────────────

    @Test
    @DisplayName("Project dashboard requires DASHBOARD_MANAGEMENT:VIEW")
    void dashboardRequiresView() {
        projectDashboardController.getDashboard();
        verify(permissionEvaluator).require("DASHBOARD_MANAGEMENT:VIEW");
    }
}
