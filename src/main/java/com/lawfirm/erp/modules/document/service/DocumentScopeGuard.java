package com.lawfirm.erp.modules.document.service;

import com.lawfirm.erp.auth.security.ReadScopeGuard;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.repository.CaseAssignmentRepository;
import com.lawfirm.erp.modules.casemanagement.service.MatterScopeGuard;
import com.lawfirm.erp.modules.document.entity.Document;
import com.lawfirm.erp.modules.document.enums.DocumentVisibility;
import com.lawfirm.erp.modules.projectmanagement.entity.Project;
import com.lawfirm.erp.modules.projectmanagement.repository.ProjectMemberRepository;
import com.lawfirm.erp.modules.projectmanagement.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DocumentScopeGuard {

    private final ReadScopeGuard readScopeGuard;
    private final MatterScopeGuard matterScopeGuard;
    private final CaseAssignmentRepository caseAssignmentRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;

    public void requireUploadAllowed(Matter matter) {
        refuseClients();
        requireMatterAssigned(matter.getId(), matter.getFirmId());
    }

    public void requireUploadAllowed(Project project) {
        refuseClients();
        requireProjectMember(project.getId());
    }

    public void requireStaffAction(Document document) {
        refuseClients();
        requireOwnerAccess(document);
    }

    public void requireVisible(Document document) {
        if (readScopeGuard.isClientScope()) {
            if (document.getVisibility() != DocumentVisibility.SHARED) {
                throw new ForbiddenException("This document has not been shared with you");
            }
            if (document.getMatterId() != null) {
                matterScopeGuard.requireVisible(document.getMatterId(), document.getFirmId(), "this document");
            } else {
                requireProjectOwnedByClient(document.getProjectId());
            }
            return;
        }
        requireOwnerAccess(document);
    }

    public void requireProjectReadable(Project project) {
        if (readScopeGuard.isClientScope()) {
            UUID me = readScopeGuard.currentUserId();
            if (project.getClientUserId() == null || !project.getClientUserId().equals(me)) {
                throw new ForbiddenException("You do not have access to this project");
            }
        }
    }

    private void requireOwnerAccess(Document document) {
        if (document.getMatterId() != null) {
            requireMatterAssigned(document.getMatterId(), document.getFirmId());
        } else if (document.getProjectId() != null) {
            requireProjectMember(document.getProjectId());
        }
    }

    private void refuseClients() {
        if (readScopeGuard.isClientScope()) {
            throw new ForbiddenException("Client accounts cannot upload or change documents");
        }
    }

    private void requireMatterAssigned(UUID matterId, UUID firmId) {
        if (!readScopeGuard.isAssignmentScope()) {
            return;
        }
        if (!caseAssignmentRepository.existsByMatterIdAndUserIdAndFirmId(
                matterId, readScopeGuard.currentUserId(), firmId)) {
            throw new ForbiddenException("You are not assigned to this case");
        }
    }

    private void requireProjectMember(UUID projectId) {
        if (!readScopeGuard.isAssignmentScope()) {
            return;
        }
        if (!projectMemberRepository.existsByProjectIdAndUserId(
                projectId, readScopeGuard.currentUserId())) {
            throw new ForbiddenException("You are not a member of this project");
        }
    }

    private void requireProjectOwnedByClient(UUID projectId) {
        UUID me = readScopeGuard.currentUserId();
        boolean owned = projectRepository.findById(projectId)
                .map(project -> project.getClientUserId() != null
                        && project.getClientUserId().equals(me))
                .orElse(false);
        if (!owned) {
            throw new ForbiddenException("You do not have access to this document");
        }
    }
}
