package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.common.constant.RoleCode;
import com.lawfirm.erp.common.enums.PermissionAction;
import com.lawfirm.erp.common.enums.PermissionScope;
import com.lawfirm.erp.dto.admin.response.TemplateSyncPreviewResponse;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TemplateSyncPlannerTest {

    @Mock private RoleRepository roleRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private FirmRepository firmRepository;

    @InjectMocks
    private TemplateSyncPlanner planner;

    private static final UUID FIRM_ADMIN_TEMPLATE_ID = UUID.randomUUID();
    private static final UUID PARALEGAL_TEMPLATE_ID = UUID.randomUUID();
    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID ADMIN_CLONE_ID = UUID.randomUUID();
    private static final UUID ADVOCATE_CLONE_ID = UUID.randomUUID();
    private static final UUID PARALEGAL_CLONE_ID = UUID.randomUUID();
    private static final UUID P_VIEW = UUID.randomUUID();
    private static final UUID P_EDIT = UUID.randomUUID();
    private static final UUID P_BILLING = UUID.randomUUID();

    private Firm firm;

    @BeforeEach
    void setUp() {
        firm = new Firm();
        firm.setId(FIRM_ID);
        firm.setLawFirmCode("APX");

        Permission view = perm(P_VIEW, "CASE_MANAGEMENT:VIEW");
        Permission edit = perm(P_EDIT, "CASE_MANAGEMENT:EDIT");
        Permission billing = perm(P_BILLING, "BILLING:VIEW");

        when(permissionRepository.findAllById(org.mockito.ArgumentMatchers.anyCollection()))
                .thenAnswer(inv -> {
                    Collection<UUID> ids = inv.getArgument(0);
                    List<Permission> all = List.of(view, edit, billing);
                    return all.stream().filter(p -> ids.contains(p.getId())).collect(java.util.stream.Collectors.toList());
                });
        when(firmRepository.findAllById(List.of(FIRM_ID))).thenReturn(List.of(firm));
    }

    private static Permission perm(UUID id, String code) {
        Permission p = Permission.builder()
                .code(code)
                .action(PermissionAction.VIEW)
                .scope(PermissionScope.TENANT)
                .build();
        p.setId(id);
        p.setActive(true);
        return p;
    }

    private Role role(UUID id, String code, boolean isSystem, Firm f) {
        Role r = new Role();
        r.setId(id);
        r.setRoleCode(code);
        r.setRoleName(code);
        r.setIsSystem(isSystem);
        r.setFirm(f);
        return r;
    }

    @Nested
    @DisplayName("FIRM_ADMIN template narrowing — cascade semantics (spec §4)")
    class FirmAdminNarrowing {

        @Test
        @DisplayName("Narrowing strips admin clone, cascades to employee clones and templates")
        void narrowCascadesEverywhere() {
            Role faTemplate = role(FIRM_ADMIN_TEMPLATE_ID, RoleCode.FIRM_ADMIN, true, null);
            Role paralegalTemplate = role(PARALEGAL_TEMPLATE_ID, RoleCode.PARALEGAL, true, null);
            Role adminClone = role(ADMIN_CLONE_ID, RoleCode.FIRM_ADMIN, false, firm);
            Role advocateClone = role(ADVOCATE_CLONE_ID, RoleCode.ADVOCATE, false, firm);

            when(roleRepository.findSystemRoleByCode(RoleCode.FIRM_ADMIN))
                    .thenReturn(Optional.of(faTemplate));
            when(roleRepository.findSystemRoleByCode(RoleCode.PARALEGAL))
                    .thenReturn(Optional.of(paralegalTemplate));
            when(roleRepository.findSystemRoleByCode(RoleCode.ADVOCATE))
                    .thenReturn(Optional.empty());
            when(roleRepository.findSystemRoleByCode(RoleCode.CLIENT))
                    .thenReturn(Optional.empty());
            when(roleRepository.findByParentRoleId(FIRM_ADMIN_TEMPLATE_ID))
                    .thenReturn(List.of(adminClone));

            // Template currently has VIEW+EDIT; edit is being removed
            when(rolePermissionRepository.findPermissionsByRoleId(FIRM_ADMIN_TEMPLATE_ID))
                    .thenReturn(List.of(perm(P_VIEW, "CASE_MANAGEMENT:VIEW"), perm(P_EDIT, "CASE_MANAGEMENT:EDIT")));
            // PARALEGAL template still holds EDIT → must be stripped too (fresh-onboarding gap)
            when(rolePermissionRepository.findPermissionsByRoleId(PARALEGAL_TEMPLATE_ID))
                    .thenReturn(List.of(perm(P_VIEW, "CASE_MANAGEMENT:VIEW"), perm(P_EDIT, "CASE_MANAGEMENT:EDIT")));
            // Firm admin clone holds VIEW+EDIT
            when(rolePermissionRepository.findPermissionsByRoleId(ADMIN_CLONE_ID))
                    .thenReturn(List.of(perm(P_VIEW, "CASE_MANAGEMENT:VIEW"), perm(P_EDIT, "CASE_MANAGEMENT:EDIT")));
            // Employee clone holds VIEW+EDIT → EDIT stripped by cascade
            when(rolePermissionRepository.findPermissionsByRoleId(ADVOCATE_CLONE_ID))
                    .thenReturn(List.of(perm(P_VIEW, "CASE_MANAGEMENT:VIEW"), perm(P_EDIT, "CASE_MANAGEMENT:EDIT")));
            when(roleRepository.findByFirmIdAndIsSystemFalse(FIRM_ID))
                    .thenReturn(List.of(adminClone, advocateClone));

            TemplateSyncPreviewResponse result =
                    planner.plan(faTemplate, Set.of(), Set.of(P_EDIT), Set.of(P_VIEW), true).build();

            assertEquals(List.of("CASE_MANAGEMENT:EDIT"), result.getRemovedPermissionCodes());
            assertEquals(1, result.getCascadeStrippedFromTemplates());
            assertEquals(Map.of(RoleCode.PARALEGAL, List.of("CASE_MANAGEMENT:EDIT")),
                    result.getEmployeeTemplateStrips());

            assertEquals(1, result.getFirmImpacts().size());
            var cloneImpact = result.getFirmImpacts().get(0).getCloneImpacts().get(0);
            assertEquals(RoleCode.FIRM_ADMIN, cloneImpact.getRoleCode());
            assertEquals(List.of("CASE_MANAGEMENT:EDIT"), cloneImpact.getRemoved());
            assertEquals(List.of("CASE_MANAGEMENT:EDIT"), cloneImpact.getCascadeStripped());
            assertEquals(1, result.getCascadeStrippedFromClones());
            assertEquals(1, result.getClonesSynced());
        }

        @Test
        @DisplayName("Widening FIRM_ADMIN never auto-grants employees — only admin clones widen")
        void wideningDoesNotTouchEmployees() {
            Role faTemplate = role(FIRM_ADMIN_TEMPLATE_ID, RoleCode.FIRM_ADMIN, true, null);
            Role adminClone = role(ADMIN_CLONE_ID, RoleCode.FIRM_ADMIN, false, firm);
            Role advocateClone = role(ADVOCATE_CLONE_ID, RoleCode.ADVOCATE, false, firm);

            when(roleRepository.findSystemRoleByCode(RoleCode.FIRM_ADMIN))
                    .thenReturn(Optional.of(faTemplate));
            when(roleRepository.findByParentRoleId(FIRM_ADMIN_TEMPLATE_ID))
                    .thenReturn(List.of(adminClone));
            when(rolePermissionRepository.findPermissionsByRoleId(FIRM_ADMIN_TEMPLATE_ID))
                    .thenReturn(List.of(perm(P_VIEW, "CASE_MANAGEMENT:VIEW")));
            when(rolePermissionRepository.findPermissionsByRoleId(ADMIN_CLONE_ID))
                    .thenReturn(List.of(perm(P_VIEW, "CASE_MANAGEMENT:VIEW")));
            when(rolePermissionRepository.findPermissionsByRoleId(ADVOCATE_CLONE_ID))
                    .thenReturn(List.of(perm(P_VIEW, "CASE_MANAGEMENT:VIEW")));
            when(roleRepository.findByFirmIdAndIsSystemFalse(FIRM_ID))
                    .thenReturn(List.of(adminClone, advocateClone));

            TemplateSyncPreviewResponse result =
                    planner.plan(faTemplate, Set.of(P_BILLING), Set.of(), Set.of(P_VIEW, P_BILLING), false).build();

            var cloneImpact = result.getFirmImpacts().get(0).getCloneImpacts().get(0);
            assertEquals(RoleCode.FIRM_ADMIN, cloneImpact.getRoleCode());
            assertEquals(List.of("BILLING:VIEW"), cloneImpact.getAdded());
            assertTrue(cloneImpact.getCascadeStripped().isEmpty());
            assertEquals(0, result.getCascadeStrippedFromClones());
            assertEquals(0, result.getCascadeStrippedFromTemplates());
        }
    }

    @Nested
    @DisplayName("Employee template edits — ceiling-gated adds (spec §4)")
    class EmployeeTemplateEdits {

        @Test
        @DisplayName("Add within firm admin ceiling → applied; over ceiling → skipped")
        void addsAreCeilingGated() {
            Role paralegalTemplate = role(PARALEGAL_TEMPLATE_ID, RoleCode.PARALEGAL, true, null);
            Role adminClone = role(ADMIN_CLONE_ID, RoleCode.FIRM_ADMIN, false, firm);
            Role paralegalClone = role(PARALEGAL_CLONE_ID, RoleCode.PARALEGAL, false, firm);

            when(roleRepository.findByParentRoleId(PARALEGAL_TEMPLATE_ID))
                    .thenReturn(List.of(paralegalClone));
            when(roleRepository.findByFirmIdAndRoleCode(FIRM_ID, RoleCode.FIRM_ADMIN))
                    .thenReturn(Optional.of(adminClone));
            // Firm admin holds VIEW only — BILLING add must be skipped
            when(rolePermissionRepository.findPermissionsByRoleId(ADMIN_CLONE_ID))
                    .thenReturn(List.of(perm(P_VIEW, "CASE_MANAGEMENT:VIEW")));
            // Paralegal clone holds nothing yet
            when(rolePermissionRepository.findPermissionsByRoleId(PARALEGAL_CLONE_ID))
                    .thenReturn(List.of());
            when(rolePermissionRepository.findPermissionsByRoleId(PARALEGAL_TEMPLATE_ID))
                    .thenReturn(List.of());

            TemplateSyncPreviewResponse result = planner.plan(
                    paralegalTemplate,
                    Set.of(P_VIEW, P_BILLING),
                    Set.of(),
                    Set.of(P_VIEW, P_BILLING),
                    false).build();

            var cloneImpact = result.getFirmImpacts().get(0).getCloneImpacts().get(0);
            assertEquals(RoleCode.PARALEGAL, cloneImpact.getRoleCode());
            assertEquals(List.of("CASE_MANAGEMENT:VIEW"), cloneImpact.getAdded());
            assertEquals(List.of("BILLING:VIEW"), cloneImpact.getSkippedByCeiling());
            assertEquals(1, result.getClonesSynced());
            assertEquals(1, result.getClonesSkippedByCeiling());
            assertEquals(0, result.getCascadeStrippedFromClones());
        }

        @Test
        @DisplayName("Employee template removals hit clones unconditionally — no cascade")
        void removalsUnconditional() {
            Role paralegalTemplate = role(PARALEGAL_TEMPLATE_ID, RoleCode.PARALEGAL, true, null);
            Role adminClone = role(ADMIN_CLONE_ID, RoleCode.FIRM_ADMIN, false, firm);
            Role paralegalClone = role(PARALEGAL_CLONE_ID, RoleCode.PARALEGAL, false, firm);

            when(roleRepository.findByParentRoleId(PARALEGAL_TEMPLATE_ID))
                    .thenReturn(List.of(paralegalClone));
            when(rolePermissionRepository.findPermissionsByRoleId(PARALEGAL_TEMPLATE_ID))
                    .thenReturn(List.of(perm(P_VIEW, "CASE_MANAGEMENT:VIEW")));
            when(rolePermissionRepository.findPermissionsByRoleId(PARALEGAL_CLONE_ID))
                    .thenReturn(List.of(perm(P_VIEW, "CASE_MANAGEMENT:VIEW")));

            TemplateSyncPreviewResponse result = planner.plan(
                    paralegalTemplate, Set.of(), Set.of(P_VIEW), Set.of(), false).build();

            var cloneImpact = result.getFirmImpacts().get(0).getCloneImpacts().get(0);
            assertEquals(List.of("CASE_MANAGEMENT:VIEW"), cloneImpact.getRemoved());
            assertTrue(cloneImpact.getCascadeStripped().isEmpty());
            assertTrue(result.getEmployeeTemplateStrips().isEmpty());
        }
    }

    @Nested
    @DisplayName("Chain validation (spec §4)")
    class ChainValidation {

        @Test
        @DisplayName("Employee template holding perms the FIRM_ADMIN template lacks → offenders named")
        void violationsNamed() {
            Role faTemplate = role(FIRM_ADMIN_TEMPLATE_ID, RoleCode.FIRM_ADMIN, true, null);
            Role paralegalTemplate = role(PARALEGAL_TEMPLATE_ID, RoleCode.PARALEGAL, true, null);

            when(roleRepository.findSystemRoleByCode(RoleCode.FIRM_ADMIN))
                    .thenReturn(Optional.of(faTemplate));
            when(rolePermissionRepository.findPermissionsByRoleId(FIRM_ADMIN_TEMPLATE_ID))
                    .thenReturn(List.of(perm(P_VIEW, "CASE_MANAGEMENT:VIEW")));

            List<String> violations = planner.validateEmployeeTemplateChain(
                    paralegalTemplate, Set.of(P_VIEW, P_BILLING));

            assertEquals(List.of("BILLING:VIEW"), violations);
        }

        @Test
        @DisplayName("Subset of FIRM_ADMIN template → no violations")
        void withinChain_ok() {
            Role faTemplate = role(FIRM_ADMIN_TEMPLATE_ID, RoleCode.FIRM_ADMIN, true, null);
            Role paralegalTemplate = role(PARALEGAL_TEMPLATE_ID, RoleCode.PARALEGAL, true, null);

            when(roleRepository.findSystemRoleByCode(RoleCode.FIRM_ADMIN))
                    .thenReturn(Optional.of(faTemplate));
            when(rolePermissionRepository.findPermissionsByRoleId(FIRM_ADMIN_TEMPLATE_ID))
                    .thenReturn(List.of(perm(P_VIEW, "CASE_MANAGEMENT:VIEW"), perm(P_EDIT, "CASE_MANAGEMENT:EDIT")));

            List<String> violations = planner.validateEmployeeTemplateChain(
                    paralegalTemplate, Set.of(P_VIEW));

            assertTrue(violations.isEmpty());
        }
    }
}
