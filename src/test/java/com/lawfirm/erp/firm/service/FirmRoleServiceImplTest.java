package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.enums.PermissionAction;
import com.lawfirm.erp.common.enums.PermissionScope;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.PermissionEvaluator;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression tests for the role-permission ceiling fix: a firm role may hold
 * any permission that its parent SYSTEM role holds — the old static scope
 * ceiling (ADVOCATE/PARALEGAL -> ASSIGNED, CLIENT -> OWN) rejected every
 * seeded TENANT-scoped permission, so firm admins could never edit those
 * roles' permissions at all.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FirmRoleServiceImplTest {

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
    private static final UUID ROLE_ID = UUID.randomUUID();
    private static final UUID PARENT_ROLE_ID = UUID.randomUUID();
    private static final UUID PERM_VIEW = UUID.randomUUID();
    private static final UUID PERM_GLOBAL = UUID.randomUUID();

    private Role firmAdvocateRole;
    private Permission caseViewPermission;
    private Permission globalPermission;

    @BeforeEach
    void setUp() {
        Firm firm = new Firm();
        firm.setId(FIRM_ID);

        firmAdvocateRole = new Role();
        firmAdvocateRole.setId(ROLE_ID);
        firmAdvocateRole.setRoleCode("ADVOCATE");
        firmAdvocateRole.setIsSystem(false);
        firmAdvocateRole.setFirm(firm);
        firmAdvocateRole.setParentRoleId(PARENT_ROLE_ID);

        Role parentAdvocateRole = new Role();
        parentAdvocateRole.setId(PARENT_ROLE_ID);
        parentAdvocateRole.setRoleCode("ADVOCATE");
        parentAdvocateRole.setIsSystem(true);

        caseViewPermission = Permission.builder()
                .code("CASE_MANAGEMENT:VIEW")
                .action(PermissionAction.VIEW)
                .scope(PermissionScope.TENANT) // all seeded permissions are TENANT-scoped
                .build();
        caseViewPermission.setActive(true);
        caseViewPermission.setId(PERM_VIEW);

        globalPermission = Permission.builder()
                .code("SYSTEM_CONFIG:ACCESS")
                .action(PermissionAction.ACCESS)
                .scope(PermissionScope.GLOBAL)
                .build();
        globalPermission.setActive(true);
        globalPermission.setId(PERM_GLOBAL);

        when(currentUserResolver.getCurrentFirmId()).thenReturn(FIRM_ID);
        when(currentUserResolver.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(firmAdvocateRole));
        when(roleRepository.findById(PARENT_ROLE_ID)).thenReturn(Optional.of(parentAdvocateRole));
        when(userRepository.findUserIdsByRoleId(ROLE_ID)).thenReturn(List.of());
    }

    @Nested
    @DisplayName("Firm role permission updates (ceiling = parent system role's permission set)")
    class CeilingEnforcement {

        @Test
        @DisplayName("TENANT-scoped permission held by parent ADVOCATE role → allowed (was 403 before the fix)")
        void parentSetPermission_allowed() {
            when(rolePermissionRepository.findPermissionsByRoleId(PARENT_ROLE_ID))
                    .thenReturn(List.of(caseViewPermission));
            when(permissionRepository.findAllById(List.of(PERM_VIEW))).thenReturn(List.of(caseViewPermission));

            RolePermissionRequest request = new RolePermissionRequest();
            request.setRoleId(ROLE_ID);
            request.setPermissionIds(List.of(PERM_VIEW));

            firmRoleService.updateRolePermissions(ROLE_ID, request);

            verify(rolePermissionRepository).deleteByRoleId(ROLE_ID);
            verify(rolePermissionRepository).saveAll(any());
        }

        @Test
        @DisplayName("Permission NOT in parent role's set → forbidden, nothing persisted")
        void outsideParentSet_forbidden() {
            when(rolePermissionRepository.findPermissionsByRoleId(PARENT_ROLE_ID))
                    .thenReturn(List.of(caseViewPermission));
            when(permissionRepository.findAllById(List.of(PERM_GLOBAL))).thenReturn(List.of(globalPermission));

            RolePermissionRequest request = new RolePermissionRequest();
            request.setRoleId(ROLE_ID);
            request.setPermissionIds(List.of(PERM_GLOBAL));

            ForbiddenException ex = assertThrows(ForbiddenException.class,
                    () -> firmRoleService.updateRolePermissions(ROLE_ID, request));
            assertTrue(ex.getMessage().contains("ADVOCATE"));
            assertTrue(ex.getMessage().contains("ceiling"));
            verify(rolePermissionRepository, never()).deleteByRoleId(any());
            verify(rolePermissionRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("GLOBAL-scope permission is never allowed, even if the parent holds it")
        void globalScope_neverAllowed() {
            when(rolePermissionRepository.findPermissionsByRoleId(PARENT_ROLE_ID))
                    .thenReturn(List.of(globalPermission));
            when(permissionRepository.findAllById(List.of(PERM_GLOBAL))).thenReturn(List.of(globalPermission));

            RolePermissionRequest request = new RolePermissionRequest();
            request.setRoleId(ROLE_ID);
            request.setPermissionIds(List.of(PERM_GLOBAL));

            assertThrows(ForbiddenException.class,
                    () -> firmRoleService.updateRolePermissions(ROLE_ID, request));
            verify(rolePermissionRepository, never()).deleteByRoleId(any());
            verify(rolePermissionRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Invalid permission ID → ResourceNotFoundException")
        void invalidPermissionId_throws() {
            UUID badId = UUID.randomUUID();
            when(permissionRepository.findAllById(List.of(badId))).thenReturn(List.of());

            RolePermissionRequest request = new RolePermissionRequest();
            request.setRoleId(ROLE_ID);
            request.setPermissionIds(List.of(badId));

            assertThrows(com.lawfirm.erp.common.exception.ResourceNotFoundException.class,
                    () -> firmRoleService.updateRolePermissions(ROLE_ID, request));
            verify(rolePermissionRepository, never()).saveAll(any());
        }
    }
}
