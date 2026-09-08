package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.common.constant.RoleCode;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.admin.response.TemplateSyncPreviewResponse.CloneImpact;
import com.lawfirm.erp.dto.admin.response.TemplateSyncPreviewResponse.FirmImpact;
import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.RolePermission;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists one firm's share of a template sync. Separate bean so the
 * per-firm @Transactional boundary is proxy-honored (same reasoning as
 * AsyncAuditWriter — @Async/@Transactional never work via self-invocation).
 *
 * Delta-apply is idempotent by construction: adds only insert missing rows,
 * removals only delete present rows — computed by the planner against fresh
 * per-firm state. Retries after partial failure are therefore safe (spec §6).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FirmSyncExecutor {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;
    private final PermissionEvaluator permissionEvaluator;
    private final AuditService auditService;

    /**
     * Applies one firm's clone delta + cascade strips in a single transaction.
     * Runs on the async sync thread — actor identity is passed explicitly
     * (no security context available).
     */
    @Transactional
    public void executeFirmSync(FirmImpact firmImpact,
                                UUID syncJobId,
                                UUID templateId,
                                UUID requestedBy) {
        for (CloneImpact cloneImpact : firmImpact.getCloneImpacts()) {
            Role clone = roleRepository.findById(cloneImpact.getRoleId()).orElse(null);
            if (clone == null) {
                continue; // clone deleted mid-sync — nothing to do for it
            }

            // ── Removals (delta only — present-on-clone already filtered) ────
            if (!cloneImpact.getRemovedPermissionIds().isEmpty()) {
                rolePermissionRepository.deleteByRoleIdAndPermissionIdIn(
                        clone.getId(), cloneImpact.getRemovedPermissionIds());
            }

            // ── Adds (ceiling-allowed only — skipped ones never arrive here) ──
            if (!cloneImpact.getAddedPermissionIds().isEmpty()) {
                // Permission references built from IDs directly — no per-add select;
                // the planner already validated existence.
                List<RolePermission> rows = cloneImpact.getAddedPermissionIds().stream()
                        .map(pid -> {
                            RolePermission rp = RolePermission.builder().role(clone).build();
                            rp.setPermission(permissionReference(pid));
                            rp.setCreatedBy(requestedBy);
                            rp.setCreatedAt(LocalDateTime.now());
                            return rp;
                        })
                        .toList();
                rolePermissionRepository.saveAll(rows);
            }

            // ── Batched invalidation: one UPDATE per affected role ───────────
            bumpForRole(clone.getId(), syncJobId, clone.getRoleCode());

            auditService.logExplicit(
                    firmImpact.getFirmId(),
                    requestedBy,
                    "S",
                    AuditAction.ROLE_PERMISSION_CHANGED,
                    AuditEntity.ROLE,
                    clone.getId(),
                    "Template sync job " + syncJobId + ": role '" + clone.getRoleCode()
                            + "' +(" + cloneImpact.getAddedPermissionIds().size() + ")/-("
                            + cloneImpact.getRemovedPermissionIds().size() + "), cascade-stripped "
                            + firmImpact.getCascadeTargets().stream()
                                    .mapToInt(t -> t.getPermissionIds().size()).sum(),
                    null // ipAddress: async worker thread has no request context
            );
        }

        // ── Narrowing cascade strips (targets carry their own role ids) ──────
        for (var target : firmImpact.getCascadeTargets()) {
            rolePermissionRepository.deleteByRoleIdAndPermissionIdIn(
                    target.getRoleId(), target.getPermissionIds());
            bumpForRole(target.getRoleId(), syncJobId, target.getRoleCode());
        }
    }

    /** Employee-template strip (spec §4b) — platform-wide, one transaction. */
    @Transactional
    public void executeTemplateStrips(Map<UUID, List<UUID>> templateStripIds,
                                      UUID syncJobId,
                                      UUID requestedBy) {
        templateStripIds.forEach((templateId, permIds) -> {
            rolePermissionRepository.deleteByRoleIdAndPermissionIdIn(templateId, permIds);
            Role template = roleRepository.findById(templateId).orElse(null);
            log.info("Sync job {}: stripped {} over-ceiling permissions from template '{}'",
                    syncJobId, permIds.size(), template != null ? template.getRoleCode() : templateId);
        });
    }

    private void bumpForRole(UUID roleId, UUID syncJobId, String roleCode) {
        userRepository.incrementPermissionVersionByRole(roleId);
        userRepository.findUserIdsByRoleId(roleId)
                .forEach(permissionEvaluator::clearUserCache);
        log.debug("Sync job {}: bumped permissionVersion for role '{}' holders", syncJobId, roleCode);
    }

    private com.lawfirm.erp.rbac.entity.Permission permissionReference(UUID permissionId) {
        // Detached id-only reference — no select; the planner already validated existence.
        com.lawfirm.erp.rbac.entity.Permission p = new com.lawfirm.erp.rbac.entity.Permission();
        p.setId(permissionId);
        return p;
    }
}
