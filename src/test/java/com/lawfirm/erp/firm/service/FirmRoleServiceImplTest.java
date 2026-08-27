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
    private static final UUID FIRM_ADMIN_ROLE_ID = UUID.randomUUID();
    private static final UUID PARENT_ROLE_ID = UUID.randomUUID();
    private static final UUID PERM_VIEW = UUID.randomUUID();
    private static final UUID PERM_CREATE = UUID.randomUUID();
    private static final UUID PERM_GLOBAL = UUID.randomUUID();

    private Role firmAdvocateRole;
    private Role firmAdminRole;
    private Role parentSystemFirmAdminRole;
    private Permission caseViewPermission;
    private Permission caseCreatePermission;
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

        firmAdminRole = new Role();
        firmAdminRole.setId(FIRM_ADMIN_ROLE_ID);
        firmAdminRole.setRoleCode("FIRM_ADMIN");
        firmAdminRole.setIsSystem(false);
        firmAdminRole.setFirm(firm);
        firmAdminRole.setParentRoleId(PARENT_ROLE_ID);

        parentSystemFirmAdminRole = new Role();
        parentSystemFirmAdminRole.setId(PARENT_ROLE_ID);
        parentSystemFirmAdminRole.setRoleCode("FIRM_ADMIN");
        parentSystemFirmAdminRole.setIsSystem(true);

        caseViewPermission = Permission.builder()
                .code("CASE_MANAGEMENT:VIEW")
                .action(PermissionAction.VIEW)
                .scope(PermissionScope.TENANT)
                .build();
        caseViewPermission.setActive(true);
        caseViewPermission.setId(PERM_VIEW);

        caseCreatePermission = Permission.builder()
                .code("CASE_MANAGEMENT:CREATE")
                .action(PermissionAction.CREATE)
                .scope(PermissionScope.TENANT)
                .build();
        caseCreatePermission.setActive(true);
        caseCreatePermission.setId(PERM_CREATE);

        globalPermission = Permission.builder()
                .code("SYSTEM_CONFIG:ACCESS")
                .action(PermissionAction.ACCESS)
                .scope(PermissionScope.GLOBAL)
                .build();
        globalPermission.setActive(true);
        globalPermission.setId(PERM_GLOBAL);

        when(currentUserResolver.getCurrentFirmId()).thenReturn(FIRM_ID);
        when(currentUserResolver.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(userRepository.findUserIdsByRoleId(ROLE_ID)).thenReturn(List.of());
    }

    @Nested
    @DisplayName("FIRM_ADMIN role: ceiling = parent system role (SUPER_ADMIN controls)")
    class FirmAdminCeiling {

        @Test
        @DisplayName("FIRM_ADMIN can get permissions that parent system FIRM_ADMIN has")
        void parentSetPermission_allowed() {
            when(roleRepository.findById(FIRM_ADMIN_ROLE_ID)).thenReturn(Optional.of(firmAdminRole));
            when(rolePermissionRepository.findPermissionsByRoleId(PARENT_ROLE_ID))
                    .thenReturn(List.of(caseViewPermission, caseCreatePermission));
            when(rolePermissionRepository.findPermissionsByRoleId(FIRM_ADMIN_ROLE_ID))
                    .thenReturn(List.of());
            when(permissionRepository.findAllById(List.of(PERM_VIEW))).thenReturn(List.of(caseViewPermission));

            RolePermissionRequest request = new RolePermissionRequest();
            request.setRoleId(FIRM_ADMIN_ROLE_ID);
            request.setPermissionIds(List.of(PERM_VIEW));

            firmRoleService.updateRolePermissions(FIRM_ADMIN_ROLE_ID, request);

            verify(rolePermissionRepository).deleteByRoleId(FIRM_ADMIN_ROLE_ID);
            verify(rolePermissionRepository).saveAll(any());
        }

        @Test
        @DisplayName("FIRM_ADMIN cannot get permission not in parent system role")
        void outsideParentSet_forbidden() {
            when(roleRepository.findById(FIRM_ADMIN_ROLE_ID)).thenReturn(Optional.of(firmAdminRole));
            when(rolePermissionRepository.findPermissionsByRoleId(PARENT_ROLE_ID))
                    .thenReturn(List.of(caseViewPermission));
            when(permissionRepository.findAllById(List.of(PERM_GLOBAL))).thenReturn(List.of(globalPermission));

            RolePermissionRequest request = new RolePermissionRequest();
            request.setRoleId(FIRM_ADMIN_ROLE_ID);
            request.setPermissionIds(List.of(PERM_GLOBAL));

            assertThrows(ForbiddenException.class,
                    () -> firmRoleService.updateRolePermissions(FIRM_ADMIN_ROLE_ID, request));
            verify(rolePermissionRepository, never()).deleteByRoleId(any());
        }
    }

    @Nested
    @DisplayName("Other firm roles: ceiling = firm FIRM_ADMIN's enabled permissions")
    class FirmRoleCeiling {

        @Test
        @DisplayName("ADVOCATE can get permission that firm FIRM_ADMIN has enabled")
        void firmAdminEnabled_allowed() {
            when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(firmAdvocateRole));
            when(roleRepository.findByFirmIdAndRoleCode(FIRM_ID, "FIRM_ADMIN"))
                    .thenReturn(Optional.of(firmAdminRole));
            when(rolePermissionRepository.findPermissionsByRoleId(FIRM_ADMIN_ROLE_ID))
                    .thenReturn(List.of(caseViewPermission, caseCreatePermission));
            when(permissionRepository.findAllById(List.of(PERM_VIEW))).thenReturn(List.of(caseViewPermission));

            RolePermissionRequest request = new RolePermissionRequest();
            request.setRoleId(ROLE_ID);
            request.setPermissionIds(List.of(PERM_VIEW));

            firmRoleService.updateRolePermissions(ROLE_ID, request);

            verify(rolePermissionRepository).deleteByRoleId(ROLE_ID);
            verify(rolePermissionRepository).saveAll(any());
        }

        @Test
        @DisplayName("ADVOCATE cannot get permission that firm FIRM_ADMIN does NOT have enabled")
        void firmAdminDoesNotHave_forbidden() {
            when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(firmAdvocateRole));
            when(roleRepository.findByFirmIdAndRoleCode(FIRM_ID, "FIRM_ADMIN"))
                    .thenReturn(Optional.of(firmAdminRole));
            when(rolePermissionRepository.findPermissionsByRoleId(FIRM_ADMIN_ROLE_ID))
                    .thenReturn(List.of(caseViewPermission));
            when(permissionRepository.findAllById(List.of(PERM_CREATE))).thenReturn(List.of(caseCreatePermission));

            RolePermissionRequest request = new RolePermissionRequest();
            request.setRoleId(ROLE_ID);
            request.setPermissionIds(List.of(PERM_CREATE));

            assertThrows(ForbiddenException.class,
                    () -> firmRoleService.updateRolePermissions(ROLE_ID, request));
            verify(rolePermissionRepository, never()).deleteByRoleId(any());
        }

        @Test
        @DisplayName("GLOBAL-scope permission is never allowed, even if FIRM_ADMIN has it")
        void globalScope_neverAllowed() {
            when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(firmAdvocateRole));
            when(roleRepository.findByFirmIdAndRoleCode(FIRM_ID, "FIRM_ADMIN"))
                    .thenReturn(Optional.of(firmAdminRole));
            when(rolePermissionRepository.findPermissionsByRoleId(FIRM_ADMIN_ROLE_ID))
                    .thenReturn(List.of(globalPermission));
            when(permissionRepository.findAllById(List.of(PERM_GLOBAL))).thenReturn(List.of(globalPermission));

            RolePermissionRequest request = new RolePermissionRequest();
            request.setRoleId(ROLE_ID);
            request.setPermissionIds(List.of(PERM_GLOBAL));

            assertThrows(ForbiddenException.class,
                    () -> firmRoleService.updateRolePermissions(ROLE_ID, request));
            verify(rolePermissionRepository, never()).deleteByRoleId(any());
        }
    }
}
