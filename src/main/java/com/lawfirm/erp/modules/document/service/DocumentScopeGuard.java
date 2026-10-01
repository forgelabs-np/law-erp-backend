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

/**
 * The one place that answers "may the caller see or act on this document?".
 *
 * <p>Mirrors {@link MatterScopeGuard}: the login identity is the scope, so callers ask this
 * guard and reject accordingly rather than inventing a second permission set.
 *
 * <ul>
 *   <li><b>Firm admin / Super Admin</b> — the whole firm, no narrowing.</li>
 *   <li><b>Firm staff</b> — only cases they are assigned to and projects they belong to;
 *       which <em>actions</em> they may take is the role permission the firm admin granted.</li>
 *   <li><b>Client</b> — own case or project, {@code SHARED} documents only, never uploads.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DocumentScopeGuard {

    private final ReadScopeGuard readScopeGuard;
    private final MatterScopeGuard matterScopeGuard;
    private final CaseAssignmentRepository caseAssignmentRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;

    /** Uploading into a case. */
    public void requireUploadAllowed(Matter matter) {
        refuseClients();
        requireMatterAssigned(matter.getId(), matter.getFirmId());
    }

    /** Uploading into a project. */
    public void requireUploadAllowed(Project project) {
        refuseClients();
        requireProjMember(project.getId());
    }

    /** Confirming or changing a document — same rules as uploading it. */
    public void requireStaffAction(Document document) {
        refuseClients();
        requireOwnerAccess(document);
    }

    /** Reading a document (including issuing a download link). */
    public void requireVisible(Document document) {
        if (readScopeGuard.isClientScope()) {
            if (document.getVisibility() != DocumentVisibility.SHARED) {
                throw new ForbiddenException("This document has not been shared with you");
            }
            if (document.getMatterId() != null) {
                // Resolves the client link from the matter column or a legacy party row.
                matterScopeGuard.requireVisible(document.getMatterId(), document.getFirmId(), "this document");
            } else {
                requireProjectOwnedByClient(document.getProjectId());
            }
            return;
        }
        requireOwnerAccess(document);
    }

    /**
     * Opening a project's document panel.
     *
     * <p>A client must own the project. For staff this is deliberately not a membership test:
     * the listing query is already narrowed to their memberships, so a non-member simply gets an
     * empty page — matching how a case panel behaves for someone who is not assigned to it.
     * Uploads and changes still require membership ({@link #requireUploadAllowed(Project)}).
     */
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

    /** A firm admin owns the whole book; everyone else works a caseload. */
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
