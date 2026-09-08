package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.common.constant.RoleCode;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.dto.admin.response.TemplateSyncPreviewResponse;
import com.lawfirm.erp.dto.admin.response.TemplateSyncPreviewResponse.CascadeStrip;
import com.lawfirm.erp.dto.admin.response.TemplateSyncPreviewResponse.CloneImpact;
import com.lawfirm.erp.dto.admin.response.TemplateSyncPreviewResponse.FirmImpact;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Computes the delta a template permission edit produces across all firm clones,
 * and the narrowing cascade required to restore the invariant:
 *
 *   no employee holds a permission their firm's FIRM_ADMIN does not hold.
 *
 * Pure computation — no writes. Preview renders it; the sync job persists it.
 * Single source of truth for sync semantics (spec §4).
 */
@Component
@RequiredArgsConstructor
public class TemplateSyncPlanner {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final FirmRepository firmRepository;

    /** Employee templates (SA-editable subset, excludes FIRM_ADMIN and SUPER_ADMIN). */
    private static final Set<String> EMPLOYEE_TEMPLATE_CODES = Set.of(
            RoleCode.ADVOCATE, RoleCode.PARALEGAL, RoleCode.CLIENT);

    /**
     * Chain validation (both directions, spec §4): an employee template's
     * permission set must always be a subset of the FIRM_ADMIN template's set.
     * Offenders are named by code; never silently clipped.
     */
    public List<String> validateEmployeeTemplateChain(Role employeeTemplate,
                                                      Set<UUID> newPermissionIds) {
        Role firmAdminTemplate = roleRepository.findSystemRoleByCode(RoleCode.FIRM_ADMIN)
                .orElseThrow(() -> new BusinessRuleException(
                        "FIRM_ADMIN system template not found — cannot validate chain"));

        Set<UUID> firmAdminPermIds = rolePermissionRepository
                .findPermissionsByRoleId(firmAdminTemplate.getId())
                .stream().map(Permission::getId).collect(Collectors.toSet());

        Map<UUID, String> codes = resolveCodes(newPermissionIds);
        return newPermissionIds.stream()
                .filter(id -> !firmAdminPermIds.contains(id))
                .map(id -> codeOrUnknown(codes, id))
                .sorted()
                .toList();
    }

    /**
     * Reverse direction of chain validation (spec §4): when SA narrows the
     * FIRM_ADMIN template, every employee template must still fit inside the
     * NEW set. Returns employee template code → over-ceiling permission codes
     * (empty map = the narrowing is legal).
     */
    public Map<String, List<String>> validateFirmAdminChain(Set<UUID> newFirmAdminPermIds) {
        Map<String, List<String>> offenders = new java.util.LinkedHashMap<>();
        for (String employeeCode : EMPLOYEE_TEMPLATE_CODES) {
            Role employeeTemplate = roleRepository.findSystemRoleByCode(employeeCode).orElse(null);
            if (employeeTemplate == null) {
                continue;
            }
            List<String> overCeiling = rolePermissionRepository
                    .findPermissionsByRoleId(employeeTemplate.getId()).stream()
                    .filter(p -> !newFirmAdminPermIds.contains(p.getId()))
                    .map(Permission::getCode)
                    .sorted()
                    .toList();
            if (!overCeiling.isEmpty()) {
                offenders.put(employeeCode, overCeiling);
            }
        }
        return offenders;
    }

    /**
     * Plans the fan-out of {@code addedIds} / {@code removedIds} to every clone
     * of {@code template}, plus the narrowing cascade when FIRM_ADMIN narrows.
     *
     * @param newTemplatePermIds  the template's POST-EDIT permission set. Preview
     *                            passes the requested set (nothing persisted yet);
     *                            the sync job reads it from the DB after commit.
     * @param narrowCascade true when this edit narrows the FIRM_ADMIN template —
     *                      employee clones/templates are re-checked against the
     *                      new ceiling.
     */
    public TemplateSyncPreviewResponse.TemplateSyncPreviewResponseBuilder plan(Role template,
                                                    Set<UUID> addedIds,
                                                    Set<UUID> removedIds,
                                                    Set<UUID> newTemplatePermIds,
                                                    boolean narrowCascade) {
        Map<UUID, String> codes = resolveCodes(union(addedIds, removedIds));

        List<FirmImpact> firmImpacts = new ArrayList<>();
        int clonesSynced = 0;
        int clonesSkippedByCeiling = 0;
        int cascadeStrippedFromClones = 0;
        int cascadeStrippedFromTemplates = 0;

        // ── Employee-template strip (FIRM_ADMIN narrowing, spec §4(b)) ────────
        // Without this, a firm onboarded after the edit clones fresh from an
        // employee template that still holds a permission the FIRM_ADMIN
        // template no longer grants — silently reintroducing the violation.
        Map<String, List<String>> employeeTemplateStrips = new HashMap<>();
        Map<UUID, List<UUID>> employeeTemplateStripIds = new HashMap<>();
        if (narrowCascade) {
            Set<UUID> newFirmAdminTemplatePerms = newTemplatePermIds;

            for (String employeeCode : EMPLOYEE_TEMPLATE_CODES) {
                Role employeeTemplate = roleRepository.findSystemRoleByCode(employeeCode).orElse(null);
                if (employeeTemplate == null) {
                    continue;
                }
                List<UUID> overCeilingIds = rolePermissionRepository
                        .findPermissionsByRoleId(employeeTemplate.getId()).stream()
                        .filter(p -> !newFirmAdminTemplatePerms.contains(p.getId()))
                        .map(Permission::getId)
                        .toList();
                if (!overCeilingIds.isEmpty()) {
                    employeeTemplateStripIds.put(employeeTemplate.getId(), overCeilingIds);
                    employeeTemplateStrips.put(employeeCode, overCeilingIds.stream()
                            .map(id -> codeOrUnknown(codes, id))
                            .sorted()
                            .toList());
                    cascadeStrippedFromTemplates += overCeilingIds.size();
                }
            }
        }

        // ── Per-firm clone fan-out ────────────────────────────────────────────
        List<Role> clones = roleRepository.findByParentRoleId(template.getId());
        Map<UUID, Firm> firmsById = firmRepository.findAllById(
                        clones.stream().map(Role::getFirm).filter(Objects::nonNull)
                                .map(Firm::getId).distinct().toList())
                .stream().collect(Collectors.toMap(Firm::getId, Function.identity()));

        for (Role clone : clones) {
            UUID firmId = clone.getFirm() != null ? clone.getFirm().getId() : null;
            if (firmId == null) {
                continue; // defensive: clones always belong to a firm
            }

            List<String> added = new ArrayList<>();
            List<String> removed = new ArrayList<>();
            List<String> skippedByCeiling = new ArrayList<>();
            List<String> cascadeStripped = new ArrayList<>();
            List<UUID> addedIdList = new ArrayList<>();
            List<UUID> removedIdList = new ArrayList<>();
            List<CascadeStrip> cascadeTargets = new ArrayList<>();

            Set<UUID> clonePermIds = rolePermissionRepository
                    .findPermissionsByRoleId(clone.getId())
                    .stream().map(Permission::getId).collect(Collectors.toSet());

            // FIRM_ADMIN template edits: the template IS the firm admin's ceiling.
            // Employee-template edits: adds must fit the firm's CURRENT FIRM_ADMIN clone.
            boolean isAdminClone = RoleCode.FIRM_ADMIN.equals(clone.getRoleCode());
            Set<UUID> firmAdminCeiling = isAdminClone ? null
                    : roleRepository.findByFirmIdAndRoleCode(firmId, RoleCode.FIRM_ADMIN)
                            .map(fa -> rolePermissionRepository.findPermissionsByRoleId(fa.getId())
                                    .stream().map(Permission::getId).collect(Collectors.toSet()))
                            .orElse(Set.of()); // firm without admin: no ceiling → adds skipped

            for (UUID addedId : addedIds) {
                if (clonePermIds.contains(addedId)) {
                    continue; // idempotent: already present
                }
                if (!isAdminClone && !firmAdminCeiling.contains(addedId)) {
                    skippedByCeiling.add(codeOrUnknown(codes, addedId));
                } else {
                    added.add(codeOrUnknown(codes, addedId));
                    addedIdList.add(addedId);
                }
            }

            for (UUID removedId : removedIds) {
                if (clonePermIds.contains(removedId)) {
                    removed.add(codeOrUnknown(codes, removedId));
                    removedIdList.add(removedId);
                }
            }

            // ── Narrowing cascade (spec §4 FIRM_ADMIN-narrows row) ────────────
            if (isAdminClone && !removedIds.isEmpty()) {
                // The firm admin just lost these — no employee may keep them.
                // Covers BOTH inherited instances and firm-added instances of the
                // same code (documented provenance tradeoff, spec §4).
                List<Role> employeeRoles = roleRepository.findByFirmIdAndIsSystemFalse(firmId).stream()
                        .filter(r -> !RoleCode.FIRM_ADMIN.equals(r.getRoleCode()))
                        .toList();

                for (Role employeeRole : employeeRoles) {
                    Set<UUID> employeePermIds = rolePermissionRepository
                            .findPermissionsByRoleId(employeeRole.getId())
                            .stream().map(Permission::getId).collect(Collectors.toSet());

                    List<UUID> strippedIds = removedIds.stream()
                            .filter(employeePermIds::contains)
                            .toList();

                    if (!strippedIds.isEmpty()) {
                        List<String> stripped = strippedIds.stream()
                                .map(id -> codeOrUnknown(codes, id))
                                .sorted()
                                .toList();
                        cascadeStripped.addAll(stripped);
                        cascadeStrippedFromClones += stripped.size();
                        cascadeTargets.add(CascadeStrip.builder()
                                .roleId(employeeRole.getId())
                                .roleCode(employeeRole.getRoleCode())
                                .permissionIds(strippedIds)
                                .permissionCodes(stripped)
                                .build());
                    }
                }
            }

            if (!added.isEmpty() || !removed.isEmpty() || !skippedByCeiling.isEmpty()
                    || !cascadeStripped.isEmpty()) {
                Firm firm = firmsById.get(firmId);
                firmImpacts.add(FirmImpact.builder()
                        .firmId(firmId)
                        .firmCode(firm != null ? firm.getLawFirmCode() : firmId.toString())
                        .cloneImpacts(List.of(CloneImpact.builder()
                                .roleId(clone.getId())
                                .roleCode(clone.getRoleCode())
                                .added(added)
                                .removed(removed)
                                .skippedByCeiling(skippedByCeiling)
                                .cascadeStripped(cascadeStripped)
                                .addedPermissionIds(addedIdList)
                                .removedPermissionIds(removedIdList)
                                .build()))
                        .cascadeTargets(cascadeTargets)
                        .build());
            }

            if (!added.isEmpty() || !removed.isEmpty()) {
                clonesSynced++;
            }
            clonesSkippedByCeiling += skippedByCeiling.size();
        }

        return TemplateSyncPreviewResponse.builder()
                .templateId(template.getId())
                .templateCode(template.getRoleCode())
                .addedPermissionCodes(sortedCodes(codes, addedIds))
                .removedPermissionCodes(sortedCodes(codes, removedIds))
                .firmImpacts(firmImpacts)
                .firmsAffected((int) firmImpacts.stream()
                        .map(FirmImpact::getFirmId).distinct().count())
                .clonesSynced(clonesSynced)
                .clonesSkippedByCeiling(clonesSkippedByCeiling)
                .cascadeStrippedFromClones(cascadeStrippedFromClones)
                .cascadeStrippedFromTemplates(cascadeStrippedFromTemplates)
                .employeeTemplateStrips(employeeTemplateStrips)
                .employeeTemplateStripIds(employeeTemplateStripIds);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Batch code resolution — one query for all touched permissions. */
    public Map<UUID, String> resolveCodes(Collection<UUID> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Map.of();
        }
        return permissionRepository.findAllById(permissionIds).stream()
                .collect(Collectors.toMap(Permission::getId, Permission::getCode));
    }

    private static String codeOrUnknown(Map<UUID, String> codes, UUID id) {
        String code = codes.get(id);
        return code != null ? code : "unknown:" + id;
    }

    private static List<String> sortedCodes(Map<UUID, String> codes, Set<UUID> ids) {
        return ids.stream().map(id -> codeOrUnknown(codes, id)).sorted().toList();
    }

    private static Set<UUID> union(Set<UUID> a, Set<UUID> b) {
        Set<UUID> all = new HashSet<>(a);
        all.addAll(b);
        return all;
    }
}
