package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.common.constant.RoleCode;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.PermissionScope;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.request.TemplatePermissionRequest;
import com.lawfirm.erp.dto.admin.response.SyncJobStatusResponse;
import com.lawfirm.erp.dto.admin.response.TemplatePermissionResponse;
import com.lawfirm.erp.dto.admin.response.TemplateSyncPreviewResponse;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.rbac.repository.SyncJobRepository;
import com.lawfirm.erp.rbac.entity.SyncJob;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.dto.admin.response.TemplateSyncPreviewResponse.FirmImpact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Super Admin editing of system role templates — the top of the delegation chain.
 *
 * Spec: docs/rbac-delegation-chain-design.md §4–§5.
 * The INVARIANT this enforces: no employee ever holds a permission their Firm
 * Admin doesn't hold; no Firm Admin ever holds a permission the FIRM_ADMIN
 * template doesn't grant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TemplatePermissionService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PermissionEvaluator permissionEvaluator;
    private final CurrentUserResolver currentUserResolver;
    private final AuditService auditService;
    private final TemplateSyncPlanner syncPlanner;
    private final SyncJobRepository syncJobRepository;
    private final TemplateSyncRunner syncRunner;
    private final FirmRepository firmRepository;

    // ─── Read: one template with permissions ─────────────────────────────────

    @Transactional(readOnly = true)
    public TemplatePermissionResponse getTemplatePermissions(UUID templateId) {
        Role template = getEditableTemplate(templateId);
        List<Permission> current = rolePermissionRepository.findPermissionsByRoleId(template.getId());

        return TemplatePermissionResponse.builder()
                .templateId(template.getId())
                .templateCode(template.getRoleCode())
                .templateName(template.getRoleName())
                .lastSaEditAt(template.getLastSaEditAt())
                .currentPermissions(current.stream()
                        .map(this::toPermResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    // ─── Preview: dry-run, zero writes (spec §7 — cascade never silent) ──────

    @Transactional(readOnly = true)
    public TemplateSyncPreviewResponse previewTemplateChange(UUID templateId,
                                                             TemplatePermissionRequest request) {
        Role template = getEditableTemplate(templateId);
        RequestedPermissions requested = loadRequested(request.getPermissionIds());

        Set<UUID> currentIds = rolePermissionRepository.findPermissionsByRoleId(template.getId())
                .stream().map(Permission::getId).collect(Collectors.toSet());

        Set<UUID> addedIds = new HashSet<>(requested.permissions().keySet());
        addedIds.removeAll(currentIds);
        Set<UUID> removedIds = new HashSet<>(currentIds);
        removedIds.removeAll(requested.permissions().keySet());

        boolean narrowCascade = isFirmAdminTemplate(template) && !removedIds.isEmpty();

        TemplateSyncPreviewResponse.TemplateSyncPreviewResponseBuilder builder = syncPlanner.plan(
                template, addedIds, removedIds, requested.permissions().keySet(), narrowCascade);

        List<String> violations = isEmployeeTemplate(template)
                ? syncPlanner.validateEmployeeTemplateChain(template, requested.permissions().keySet())
                : List.of();

        builder.wouldViolateChain(!violations.isEmpty())
                .chainValidationViolations(violations);
        return builder.build();
    }

    // ─── Edit: validate → persist template → enqueue sync job (Phase 3) ─────

    @Transactional
    public TemplatePermissionResponse updateTemplatePermissions(UUID templateId,
                                                                TemplatePermissionRequest request) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        Role template = getEditableTemplate(templateId);
        RequestedPermissions requested = loadRequested(request.getPermissionIds());

        Set<UUID> requestedIds = requested.permissions().keySet();
        Set<UUID> currentIds = rolePermissionRepository.findPermissionsByRoleId(template.getId())
                .stream().map(Permission::getId).collect(Collectors.toSet());

        // ── Chain validation BEFORE persisting (spec §4, both directions) ────
        if (isEmployeeTemplate(template)) {
            List<String> violations =
                    syncPlanner.validateEmployeeTemplateChain(template, requestedIds);
            if (!violations.isEmpty()) {
                // Name the offending codes — never silently clip (spec §4).
                throw new BusinessRuleException(
                        "Edit violates the delegation chain: employee template '"
                        + template.getRoleCode()
                        + "' cannot hold permissions the FIRM_ADMIN template does not grant: "
                        + String.join(", ", violations));
            }
        } else if (isFirmAdminTemplate(template)) {
            // Chain validation in BOTH directions (spec §4): narrowing FIRM_ADMIN
            // below what an employee template already holds would strand that
            // template over ceiling. SA must narrow employee templates first —
            // the error names the employee template(s) and codes (spec §2.4).
            Map<String, List<String>> offenders =
                    syncPlanner.validateFirmAdminChain(requestedIds);
            if (!offenders.isEmpty()) {
                String detail = offenders.entrySet().stream()
                        .map(e -> e.getKey() + " holds [" + String.join(", ", e.getValue()) + "]")
                        .collect(Collectors.joining("; "));
                throw new BusinessRuleException(
                        "Edit violates the delegation chain: narrowing FIRM_ADMIN below "
                        + "existing employee templates is blocked. Narrow the employee "
                        + "template(s) first: " + detail);
            }
        }

        Set<UUID> addedIds = new HashSet<>(requestedIds);
        addedIds.removeAll(currentIds);
        Set<UUID> removedIds = new HashSet<>(currentIds);
        removedIds.removeAll(requestedIds);

        // ── Persist template ─────────────────────────────────────────────────
        rolePermissionRepository.deleteByRoleId(template.getId());
        List<com.lawfirm.erp.rbac.entity.RolePermission> rows =
                requested.permissions().values().stream()
                        .map(p -> com.lawfirm.erp.rbac.entity.RolePermission.builder()
                                .role(template)
                                .permission(p)
                                .build())
                        .collect(Collectors.toList());
        rows.forEach(rp -> {
            rp.setCreatedBy(adminId);
            rp.setCreatedAt(LocalDateTime.now());
        });
        rolePermissionRepository.saveAll(rows);

        // ── Seeder freeze (spec §3) ─────────────────────────────────────────
        // Any SA edit freezes the template against boot re-seeding; otherwise a
        // restart resurrects removed rows and re-breaks the invariant.
        template.setLastSaEditAt(LocalDateTime.now());
        roleRepository.save(template);

        // ── Invalidations: template holders (rare — clones hold real users) ──
        bumpVersionsForRole(template.getId());

        // ── Plan the fan-out POST-persist, then enqueue the async job (§6) ──
        boolean narrowCascade = isFirmAdminTemplate(template) && !removedIds.isEmpty();
        TemplateSyncPreviewResponse plan = syncPlanner.plan(
                template, addedIds, removedIds, requestedIds, narrowCascade).build();

        SyncJob job = SyncJob.builder()
                .templateId(template.getId())
                .templateCode(template.getRoleCode())
                .status(SyncJob.SyncStatus.PENDING)
                .build();
        job = syncJobRepository.save(job);

        final UUID jobId = job.getId();
        final UUID syncedTemplateId = template.getId();
        final List<FirmImpact> firmImpacts = plan.getFirmImpacts();
        final Map<UUID, List<UUID>> templateStripIds = plan.getEmployeeTemplateStripIds();
        final UUID requestedBy = adminId;

        // Enqueue AFTER COMMIT: the async thread reads sync_jobs (and the new
        // template state) — neither is visible to it until this tx commits.
        // Without this, the runner can race the commit and abort "job vanished".
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncRunner.runSync(jobId, syncedTemplateId, firmImpacts, templateStripIds, requestedBy);
                }
            });
        } else {
            syncRunner.runSync(jobId, syncedTemplateId, firmImpacts, templateStripIds, requestedBy);
        }

        auditService.log(
                AuditAction.TEMPLATE_PERMISSION_CHANGED,
                AuditEntity.ROLE,
                template.getId(),
                "Super Admin edited template '" + template.getRoleCode() + "': "
                        + addedIds.size() + " added, " + removedIds.size() + " removed"
                        + ". Sync job " + jobId + " enqueued ("
                        + plan.getFirmImpacts().size() + " firms)."
        );

        log.info("Template '{}' edited by SA {}: +{} / -{} — sync job {} enqueued ({} firms)",
                template.getRoleCode(), adminId, addedIds.size(), removedIds.size(),
                jobId, plan.getFirmImpacts().size());

        TemplatePermissionResponse response = buildResponse(template);
        response.setSyncJobId(jobId);
        return response;
    }

    // ─── Sync job status (SA polling, spec §5) ───────────────────────────────

    @Transactional(readOnly = true)
    public SyncJobStatusResponse getSyncJobStatus(UUID jobId) {
        SyncJob job = syncJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Sync job not found: " + jobId));
        return SyncJobStatusResponse.builder()
                .jobId(job.getId())
                .templateId(job.getTemplateId())
                .templateCode(job.getTemplateCode())
                .status(job.getStatus().name())
                .firmsTotal(job.getFirmsTotal())
                .firmsCompleted(job.getFirmsCompleted())
                .firmsFailed(job.getFirmsFailed())
                .errorSummary(job.getErrorSummary())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }

    // ─── Guards & helpers ─────────────────────────────────────────────────────

    /** Load + validate the target: system template, SA-editable subset. */
    private Role getEditableTemplate(UUID templateId) {
        Role role = roleRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + templateId));

        if (!Boolean.TRUE.equals(role.getIsSystem()) || role.getFirm() != null) {
            throw new BusinessRuleException(
                    "Not a system role template: " + role.getRoleCode()
                    + " — use the firm-role endpoints for clones");
        }
        if (RoleCode.SUPER_ADMIN.equals(role.getRoleCode())) {
            throw new BusinessRuleException(
                    "SUPER_ADMIN template is immutable — SA authorization is a hardcoded bypass, "
                    + "editing it would be pure ceremony and a lockout risk (spec §1)");
        }
        return role;
    }

    private boolean isFirmAdminTemplate(Role template) {
        return RoleCode.FIRM_ADMIN.equals(template.getRoleCode());
    }

    private boolean isEmployeeTemplate(Role template) {
        return RoleCode.ADVOCATE.equals(template.getRoleCode())
                || RoleCode.PARALEGAL.equals(template.getRoleCode())
                || RoleCode.CLIENT.equals(template.getRoleCode());
    }

    private RequestedPermissions loadRequested(List<UUID> permissionIds) {
        if (permissionIds == null) {
            throw new BusinessRuleException("permissionIds list is required");
        }
        List<Permission> permissions = permissionRepository.findAllById(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            throw new ResourceNotFoundException("One or more permission IDs are invalid");
        }
        for (Permission p : permissions) {
            if (p.getScope() == PermissionScope.GLOBAL) {
                throw new BusinessRuleException(
                        "GLOBAL-scope permissions are reserved for Super Admin and cannot be "
                        + "granted to templates: " + p.getCode());
            }
        }
        Map<UUID, Permission> byId = permissions.stream()
                .collect(Collectors.toMap(Permission::getId, p -> p));
        return new RequestedPermissions(byId);
    }

    private void bumpVersionsForRole(UUID roleId) {
        List<UUID> userIds = userRepository.findUserIdsByRoleId(roleId);
        for (UUID userId : userIds) {
            userRepository.incrementPermissionVersion(userId);
            permissionEvaluator.clearUserCache(userId);
        }
    }

    private TemplatePermissionResponse buildResponse(Role template) {
        List<Permission> current = rolePermissionRepository.findPermissionsByRoleId(template.getId());
        return TemplatePermissionResponse.builder()
                .templateId(template.getId())
                .templateCode(template.getRoleCode())
                .templateName(template.getRoleName())
                .lastSaEditAt(template.getLastSaEditAt())
                .currentPermissions(current.stream()
                        .map(this::toPermResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    private com.lawfirm.erp.dto.admin.response.PermissionResponse toPermResponse(Permission p) {
        return com.lawfirm.erp.dto.admin.response.PermissionResponse.builder()
                .id(p.getId())
                .action(p.getAction())
                .scope(p.getScope())
                .code(p.getCode())
                .description(p.getDescription())
                .isActive(p.isActive())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private record RequestedPermissions(Map<UUID, Permission> permissions) {}
}
