package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.PermissionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FirmRoleDeleteDefaultTest {

    @Mock private FirmRepository firmRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private PermissionEvaluator permissionEvaluator;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private AuditService auditService;

    @InjectMocks
    private FirmRoleServiceImpl firmRoleService;

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(currentUserResolver.getCurrentFirmId()).thenReturn(FIRM_ID);
        when(currentUserResolver.getCurrentUserId()).thenReturn(ADMIN_ID);
    }

    private Role buildFirmRole(String roleCode) {
        Firm firm = new Firm();
        firm.setId(FIRM_ID);
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setRoleCode(roleCode);
        role.setRoleName(roleCode);
        role.setIsSystem(false);
        role.setFirm(firm);
        role.setActive(true);
        return role;
    }

    @Test
    @DisplayName("Cannot delete ADVOCATE role (default cloned role)")
    void deleteRole_cannotDeleteAdvocate() {
        Role advocateRole = buildFirmRole("ADVOCATE");
        when(roleRepository.findById(advocateRole.getId())).thenReturn(Optional.of(advocateRole));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> firmRoleService.deleteRole(advocateRole.getId()));
        assertTrue(ex.getMessage().contains("ADVOCATE"));
        assertTrue(ex.getMessage().contains("default role"));
    }

    @Test
    @DisplayName("Cannot delete PARALEGAL role (default cloned role)")
    void deleteRole_cannotDeleteParalegal() {
        Role paralegalRole = buildFirmRole("PARALEGAL");
        when(roleRepository.findById(paralegalRole.getId())).thenReturn(Optional.of(paralegalRole));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> firmRoleService.deleteRole(paralegalRole.getId()));
        assertTrue(ex.getMessage().contains("PARALEGAL"));
    }

    @Test
    @DisplayName("Cannot delete CLIENT role (default cloned role)")
    void deleteRole_cannotDeleteClient() {
        Role clientRole = buildFirmRole("CLIENT");
        when(roleRepository.findById(clientRole.getId())).thenReturn(Optional.of(clientRole));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> firmRoleService.deleteRole(clientRole.getId()));
        assertTrue(ex.getMessage().contains("CLIENT"));
    }

    @Test
    @DisplayName("Can delete custom role (not a default cloned role)")
    void deleteRole_canDeleteCustomRole() {
        Role customRole = buildFirmRole("SENIOR_ADVOCATE");
        when(roleRepository.findById(customRole.getId())).thenReturn(Optional.of(customRole));
        when(roleRepository.countUsersByRoleId(customRole.getId())).thenReturn(0);

        assertDoesNotThrow(() -> firmRoleService.deleteRole(customRole.getId()));
        verify(roleRepository).delete(customRole);
    }
}
