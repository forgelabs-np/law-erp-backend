package com.lawfirm.erp.modules.me.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.entity.FirmModule;
import com.lawfirm.erp.firm.repository.FirmModuleRepository;
import com.lawfirm.erp.modules.me.dto.MeResponse;
import com.lawfirm.erp.modules.me.mapper.MeMapper;
import com.lawfirm.erp.rbac.entity.Module;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.ModuleRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private FirmModuleRepository firmModuleRepository;
    @Mock private ModuleRepository moduleRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private SystemConfigService systemConfigService;
    @Mock private MeMapper meMapper;

    @InjectMocks
    private MeServiceImpl meService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID ROLE_ID = UUID.randomUUID();

    // ── Helpers ────────────────────────────────────────────────────────────

    private Module module(String code, String name, Module parent) {
        Module m = Module.builder()
                .code(code)
                .name(name)
                .icon(code + "Icon")
                .path("/" + code.toLowerCase())
                .build();
        m.setParent(parent);
        m.setActive(true);
        return m;
    }

    private User firmUser(Firm firm, Role role) {
        return User.builder()
                .userType(UserType.FIRM_USER)
                .firm(firm)
                .role(role)
                .username("advocate1")
                .fullName("Advocate One")
                .email("advocate@firm.com")
                .mobileNo("9800000000")
                .build();
    }

    private User superAdmin() {
        return User.builder()
                .userType(UserType.SUPER_ADMIN)
                .username("super")
                .fullName("Super Admin")
                .email("super@system.com")
                .build();
    }

    private Role role() {
        return Role.builder().roleName("ADVOCATE").roleCode("ADVOCATE").build();
    }

    private Firm firm() {
        Firm f = new Firm();
        f.setId(FIRM_ID);
        return f;
    }

    private Permission perm(String code) {
        return Permission.builder().code(code).build();
    }

    private FirmModule firmModule(Module m, boolean enabled) {
        return FirmModule.builder()
                .module(m)
                .isEnabled(enabled)
                .expiresAt(null)
                .build();
    }

    private void stubBasics(User user) {
        when(currentUserResolver.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(systemConfigService.getGlobal(any())).thenReturn(Optional.of("NepalCRM"));
        // Lenient: only firm users with a firm hit getFirm (branding lookup)
        lenient().when(systemConfigService.getFirm(any(), any())).thenReturn(Optional.empty());
    }

    // ── Tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SUPER_ADMIN sees all active modules with sub-modules nested inside")
    void superAdmin_moduleTreeIsNested() {
        User user = superAdmin();
        stubBasics(user);

        Module caseMgmt = module("CASE_MANAGEMENT", "Case Management", null);
        Module hearings = module("HEARINGS", "Hearings", caseMgmt);
        Module calendar = module("CALENDAR", "Calendar", null);
        when(moduleRepository.findAllWithParentOrderByDisplayOrder())
                .thenReturn(List.of(caseMgmt, hearings, calendar));

        MeResponse response = meService.getMe();

        // 2 roots: CASE_MANAGEMENT and CALENDAR. HEARINGS must NOT be a root.
        List<MeResponse.ModuleAccess> roots = response.getModules();
        assertEquals(2, roots.size());
        assertEquals(List.of("CASE_MANAGEMENT", "CALENDAR"),
                roots.stream().map(MeResponse.ModuleAccess::getModuleCode).toList());

        // CASE_MANAGEMENT has HEARINGS nested inside it
        MeResponse.ModuleAccess caseAccess = roots.get(0);
        assertNotNull(caseAccess.getSubModules());
        assertEquals(1, caseAccess.getSubModules().size());
        assertEquals("HEARINGS", caseAccess.getSubModules().get(0).getModuleCode());
        assertTrue(caseAccess.getSubModules().get(0).getSubModules().isEmpty());

        // CALENDAR has no children
        assertTrue(roots.get(1).getSubModules().isEmpty());
    }

    @Test
    @DisplayName("FIRM_USER: sub-module with permission nests under its parent module")
    void firmUser_subModuleNestsUnderParent() {
        Role role = role();
        User user = firmUser(firm(), role);
        stubBasics(user);

        Module caseMgmt = module("CASE_MANAGEMENT", "Case Management", null);
        Module hearings = module("HEARINGS", "Hearings", caseMgmt);
        when(moduleRepository.findAllWithParentOrderByDisplayOrder())
                .thenReturn(List.of(caseMgmt, hearings));
        when(rolePermissionRepository.findPermissionsByRoleId(any()))
                .thenReturn(List.of(perm("CASE_MANAGEMENT:VIEW"), perm("HEARINGS:VIEW")));
        when(firmModuleRepository.findByFirmIdWithModule(FIRM_ID))
                .thenReturn(List.of(firmModule(caseMgmt, true)));

        MeResponse response = meService.getMe();

        assertEquals(1, response.getModules().size());
        MeResponse.ModuleAccess caseAccess = response.getModules().get(0);
        assertEquals("CASE_MANAGEMENT", caseAccess.getModuleCode());
        assertEquals(List.of("VIEW"), caseAccess.getActions());
        assertTrue(caseAccess.isEnabled());

        assertEquals(1, caseAccess.getSubModules().size());
        MeResponse.ModuleAccess hearingAccess = caseAccess.getSubModules().get(0);
        assertEquals("HEARINGS", hearingAccess.getModuleCode());
        assertEquals(List.of("VIEW"), hearingAccess.getActions());
        assertTrue(hearingAccess.isEnabled());
    }

    @Test
    @DisplayName("FIRM_USER: parent with no direct permission is still shown as a container when a child is accessible")
    void firmUser_parentPromotedAsContainerForAccessibleChild() {
        Role role = role();
        User user = firmUser(firm(), role);
        stubBasics(user);

        Module caseMgmt = module("CASE_MANAGEMENT", "Case Management", null);
        Module hearings = module("HEARINGS", "Hearings", caseMgmt);
        when(moduleRepository.findAllWithParentOrderByDisplayOrder())
                .thenReturn(List.of(caseMgmt, hearings));
        // User only has HEARINGS:VIEW — no direct CASE_MANAGEMENT permission
        when(rolePermissionRepository.findPermissionsByRoleId(any()))
                .thenReturn(List.of(perm("HEARINGS:VIEW")));
        when(firmModuleRepository.findByFirmIdWithModule(FIRM_ID))
                .thenReturn(List.of(firmModule(caseMgmt, true)));

        MeResponse response = meService.getMe();

        assertEquals(1, response.getModules().size());
        MeResponse.ModuleAccess caseAccess = response.getModules().get(0);
        assertEquals("CASE_MANAGEMENT", caseAccess.getModuleCode());
        assertTrue(caseAccess.getActions().isEmpty());   // no direct permission
        assertTrue(caseAccess.isEnabled());              // firm has the module enabled

        assertEquals(1, caseAccess.getSubModules().size());
        assertEquals("HEARINGS", caseAccess.getSubModules().get(0).getModuleCode());
        assertEquals(List.of("VIEW"), caseAccess.getSubModules().get(0).getActions());
    }

    @Test
    @DisplayName("FIRM_USER: multi-level nesting — grandchild nests under promoted grandparent and parent")
    void firmUser_multiLevelNesting() {
        Role role = role();
        User user = firmUser(firm(), role);
        stubBasics(user);

        Module litigation = module("LITIGATION", "Litigation", null);
        Module civil = module("CIVIL", "Civil Cases", litigation);
        Module hearings = module("HEARINGS", "Hearings", civil);
        when(moduleRepository.findAllWithParentOrderByDisplayOrder())
                .thenReturn(List.of(litigation, civil, hearings));
        // User only has the deepest permission — parents must be promoted as containers
        when(rolePermissionRepository.findPermissionsByRoleId(any()))
                .thenReturn(List.of(perm("HEARINGS:VIEW")));
        when(firmModuleRepository.findByFirmIdWithModule(FIRM_ID))
                .thenReturn(List.of(firmModule(litigation, true)));

        MeResponse response = meService.getMe();

        assertEquals(1, response.getModules().size());
        MeResponse.ModuleAccess litigationAccess = response.getModules().get(0);
        assertEquals("LITIGATION", litigationAccess.getModuleCode());
        assertTrue(litigationAccess.getActions().isEmpty()); // no direct permission
        assertTrue(litigationAccess.isEnabled());

        assertEquals(1, litigationAccess.getSubModules().size());
        MeResponse.ModuleAccess civilAccess = litigationAccess.getSubModules().get(0);
        assertEquals("CIVIL", civilAccess.getModuleCode());
        assertTrue(civilAccess.getActions().isEmpty());

        assertEquals(1, civilAccess.getSubModules().size());
        assertEquals("HEARINGS", civilAccess.getSubModules().get(0).getModuleCode());
        assertEquals(List.of("VIEW"), civilAccess.getSubModules().get(0).getActions());
        assertTrue(civilAccess.getSubModules().get(0).isEnabled());
    }

    @Test
    @DisplayName("FIRM_USER: sub-module without a permission is hidden even when the parent is accessible")
    void firmUser_subModuleWithoutPermissionIsHidden() {
        Role role = role();
        User user = firmUser(firm(), role);
        stubBasics(user);

        Module caseMgmt = module("CASE_MANAGEMENT", "Case Management", null);
        Module hearings = module("HEARINGS", "Hearings", caseMgmt);
        when(moduleRepository.findAllWithParentOrderByDisplayOrder())
                .thenReturn(List.of(caseMgmt, hearings));
        // Only CASE_MANAGEMENT:VIEW — no HEARINGS permission
        when(rolePermissionRepository.findPermissionsByRoleId(any()))
                .thenReturn(List.of(perm("CASE_MANAGEMENT:VIEW")));
        when(firmModuleRepository.findByFirmIdWithModule(FIRM_ID))
                .thenReturn(List.of(firmModule(caseMgmt, true)));

        MeResponse response = meService.getMe();

        assertEquals(1, response.getModules().size());
        MeResponse.ModuleAccess caseAccess = response.getModules().get(0);
        assertEquals("CASE_MANAGEMENT", caseAccess.getModuleCode());
        assertTrue(caseAccess.getSubModules().isEmpty());
    }

    @Test
    @DisplayName("FIRM_USER with no firm gets an empty module list (no repo call)")
    void firmUserWithoutFirm_returnsEmptyModules() {
        Role role = role();
        User user = firmUser(null, role);
        stubBasics(user);

        // No moduleRepository stub needed: the service short-circuits before
        // touching modules/firm-modules when the user has no firm.
        MeResponse response = meService.getMe();
        assertNotNull(response.getModules());
        assertTrue(response.getModules().isEmpty());
    }

    @Test
    @DisplayName("Inactive modules and their children are excluded")
    void inactiveModulesExcluded() {
        Role role = role();
        User user = firmUser(firm(), role);
        stubBasics(user);

        Module caseMgmt = module("CASE_MANAGEMENT", "Case Management", null);
        Module deactivated = module("DOCUMENT_MANAGEMENT", "Document Management", null);
        deactivated.setActive(false);
        when(moduleRepository.findAllWithParentOrderByDisplayOrder())
                .thenReturn(List.of(caseMgmt, deactivated));
        when(rolePermissionRepository.findPermissionsByRoleId(any()))
                .thenReturn(List.of(perm("CASE_MANAGEMENT:VIEW"), perm("DOCUMENT_MANAGEMENT:VIEW")));
        when(firmModuleRepository.findByFirmIdWithModule(FIRM_ID)).thenReturn(List.of());

        MeResponse response = meService.getMe();

        assertEquals(1, response.getModules().size());
        assertEquals("CASE_MANAGEMENT", response.getModules().get(0).getModuleCode());
    }

    @Test
    @DisplayName("Firm user only sees modules enabled+permitted; disabled firm module is flagged enabled=false")
    void firmUser_disabledFirmModuleFlagged() {
        Role role = role();
        User user = firmUser(firm(), role);
        stubBasics(user);

        Module billing = module("BILLING", "Billing & Invoices", null);
        when(moduleRepository.findAllWithParentOrderByDisplayOrder()).thenReturn(List.of(billing));
        when(rolePermissionRepository.findPermissionsByRoleId(any()))
                .thenReturn(List.of(perm("BILLING:VIEW")));
        // BILLING is NOT enabled for this firm
        when(firmModuleRepository.findByFirmIdWithModule(FIRM_ID)).thenReturn(List.of());

        MeResponse response = meService.getMe();

        assertEquals(1, response.getModules().size());
        assertFalse(response.getModules().get(0).isEnabled());
    }

    @Test
    @DisplayName("Expired firm module is treated as disabled")
    void firmUser_expiredFirmModuleTreatedDisabled() {
        Role role = role();
        User user = firmUser(firm(), role);
        stubBasics(user);

        Module billing = module("BILLING", "Billing & Invoices", null);
        when(moduleRepository.findAllWithParentOrderByDisplayOrder()).thenReturn(List.of(billing));
        when(rolePermissionRepository.findPermissionsByRoleId(any()))
                .thenReturn(List.of(perm("BILLING:VIEW")));
        // BILLING enabled but EXPIRED -> treated as disabled
        FirmModule expired = FirmModule.builder()
                .module(billing)
                .isEnabled(true)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
        when(firmModuleRepository.findByFirmIdWithModule(FIRM_ID)).thenReturn(List.of(expired));

        MeResponse response = meService.getMe();

        assertEquals(1, response.getModules().size());
        assertFalse(response.getModules().get(0).isEnabled());
    }

    @Test
    @DisplayName("JSON: leaf modules omit subModules; only parents expose it")
    void json_leafModulesOmitSubModulesField() throws Exception {
        MeResponse.ModuleAccess leaf = MeResponse.ModuleAccess.builder()
                .moduleCode("GLOBAL_CONFIG")
                .moduleName("Global Config")
                .actions(List.of())
                .enabled(true)
                .subModules(List.of())
                .build();
        MeResponse.ModuleAccess parent = MeResponse.ModuleAccess.builder()
                .moduleCode("CONFIGURATION")
                .moduleName("Configuration")
                .actions(List.of())
                .enabled(true)
                .subModules(List.of(leaf))
                .build();
        MeResponse response = MeResponse.builder()
                .modules(List.of(parent, leaf))
                .build();

        String json = new ObjectMapper().writeValueAsString(response);
        JsonNode root = new ObjectMapper().readTree(json);

        // Parent module exposes its subModules array with the leaf inside
        JsonNode parentNode = root.get("modules").get(0);
        assertTrue(parentNode.has("subModules"), "parent should expose subModules, got: " + json);
        assertEquals("GLOBAL_CONFIG", parentNode.get("subModules").get(0).get("moduleCode").asText());

        // Leaf module must NOT contain a subModules key at all
        JsonNode leafNode = root.get("modules").get(1);
        assertFalse(leafNode.has("subModules"), "leaf should omit subModules, got: " + json);

        // And the nested leaf inside the parent is also clean
        assertFalse(parentNode.get("subModules").get(0).has("subModules"),
                "nested leaf should omit subModules, got: " + json);
    }
}
