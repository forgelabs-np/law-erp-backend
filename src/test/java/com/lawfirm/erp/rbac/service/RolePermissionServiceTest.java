package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.RolePermission;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.enums.PermissionAction;
import com.lawfirm.erp.common.enums.PermissionScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RolePermissionServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private UserRepository userRepository;
    @Mock private PermissionEvaluator permissionEvaluator;
    @Mock private com.lawfirm.erp.rbac.mapper.RbacResponseMapper rbacResponseMapper;

    @InjectMocks
    private RolePermissionServiceImpl rolePermissionService;

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID ROLE_ID = UUID.randomUUID();
    private static final UUID PERM_ID_TENANT = UUID.randomUUID();
    private static final UUID PERM_ID_GLOBAL = UUID.randomUUID();

    private Role firmAdminRole;
    private Permission tenantPermission;
    private Permission globalPermission;

    @BeforeEach
    void setUp() {
        firmAdminRole = new Role();
        firmAdminRole.setId(ROLE_ID);
        firmAdminRole.setRoleCode("FIRM_ADMIN");
        firmAdminRole.setRoleName("FIRM_ADMIN");
        firmAdminRole.setIsSystem(false);

        tenantPermission = Permission.builder()
                .code("CASE_MANAGEMENT:VIEW")
                .action(PermissionAction.VIEW)
                .scope(PermissionScope.TENANT)
                .build();
        tenantPermission.setActive(true);
        tenantPermission.setId(PERM_ID_TENANT);

        globalPermission = Permission.builder()
                .code("SYSTEM_CONFIG:ACCESS")
                .action(PermissionAction.ACCESS)
                .scope(PermissionScope.GLOBAL)
                .build();
        globalPermission.setActive(true);
        globalPermission.setId(PERM_ID_GLOBAL);

        when(currentUserResolver.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(firmAdminRole));
    }

    @Nested
    @DisplayName("GLOBAL-scope permission is always rejected")
    class GlobalScopeCheck {

        @Test
        @DisplayName("GLOBAL-scope permission → forbidden")
        void globalScope_forbidden() {
            when(permissionRepository.findAllById(List.of(PERM_ID_GLOBAL))).thenReturn(List.of(globalPermission));

            RolePermissionRequest request = buildRequest(List.of(PERM_ID_GLOBAL));

            ForbiddenException ex = assertThrows(ForbiddenException.class,
                    () -> rolePermissionService.assignPermissionsToRole(request));
            assertTrue(ex.getMessage().contains("GLOBAL"));
            verify(rolePermissionRepository, never()).deleteByRoleId(any());
            verify(rolePermissionRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("TENANT-scope permission → allowed")
        void tenantScope_allowed() {
            when(permissionRepository.findAllById(List.of(PERM_ID_TENANT))).thenReturn(List.of(tenantPermission));
            when(userRepository.findUserIdsByRoleId(ROLE_ID)).thenReturn(Collections.emptyList());

            RolePermissionRequest request = buildRequest(List.of(PERM_ID_TENANT));
            rolePermissionService.assignPermissionsToRole(request);

            verify(rolePermissionRepository).deleteByRoleId(ROLE_ID);
            verify(rolePermissionRepository).saveAll(any());
        }

        @Test
        @DisplayName("Mixed permissions → fails on GLOBAL")
        void mixedPermissions_failsOnGlobal() {
            when(permissionRepository.findAllById(List.of(PERM_ID_TENANT, PERM_ID_GLOBAL)))
                    .thenReturn(List.of(tenantPermission, globalPermission));

            RolePermissionRequest request = buildRequest(List.of(PERM_ID_TENANT, PERM_ID_GLOBAL));

            assertThrows(ForbiddenException.class,
                    () -> rolePermissionService.assignPermissionsToRole(request));
            verify(rolePermissionRepository, never()).deleteByRoleId(any());
            verify(rolePermissionRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("Role permission assignment")
    class PermissionAssignment {

        @Test
        @DisplayName("Delete is called before saveAll in same transaction")
        void deleteCalledBeforeSave() {
            when(permissionRepository.findAllById(List.of(PERM_ID_TENANT))).thenReturn(List.of(tenantPermission));
            when(userRepository.findUserIdsByRoleId(ROLE_ID)).thenReturn(Collections.emptyList());

            RolePermissionRequest request = buildRequest(List.of(PERM_ID_TENANT));
            rolePermissionService.assignPermissionsToRole(request);

            verify(rolePermissionRepository).deleteByRoleId(ROLE_ID);
            verify(rolePermissionRepository).saveAll(any());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<RolePermission>> captor = ArgumentCaptor.forClass(List.class);
            verify(rolePermissionRepository).saveAll(captor.capture());
            List<RolePermission> saved = captor.getValue();
            assertEquals(1, saved.size());
            assertEquals(ROLE_ID, saved.get(0).getRole().getId());
            assertEquals(PERM_ID_TENANT, saved.get(0).getPermission().getId());
        }

        @Test
        @DisplayName("Empty permission list → deletes all existing, saves empty list")
        void emptyPermissionList() {
            when(permissionRepository.findAllById(Collections.emptyList())).thenReturn(Collections.emptyList());
            when(userRepository.findUserIdsByRoleId(ROLE_ID)).thenReturn(Collections.emptyList());

            RolePermissionRequest request = new RolePermissionRequest();
            request.setRoleId(ROLE_ID);
            request.setPermissionIds(Collections.emptyList());

            rolePermissionService.assignPermissionsToRole(request);

            verify(rolePermissionRepository).deleteByRoleId(ROLE_ID);
            verify(rolePermissionRepository).saveAll(Collections.emptyList());
        }

        @Test
        @DisplayName("Non-existent permission ID → ResourceNotFoundException")
        void nonExistentPermission_throws() {
            UUID badId = UUID.randomUUID();
            when(permissionRepository.findAllById(List.of(badId))).thenReturn(Collections.emptyList());

            RolePermissionRequest request = buildRequest(List.of(badId));

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> rolePermissionService.assignPermissionsToRole(request));
            assertTrue(ex.getMessage().contains("permission"));
        }
    }

    @Nested
    @DisplayName("System role protection")
    class SystemRoleProtection {

        @Test
        @DisplayName("Cannot assign permissions to system role")
        void systemRole_throws() {
            firmAdminRole.setIsSystem(true);

            RolePermissionRequest request = buildRequest(List.of(PERM_ID_TENANT));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> rolePermissionService.assignPermissionsToRole(request));
            assertTrue(ex.getMessage().contains("system role"));
            verify(rolePermissionRepository, never()).deleteByRoleId(any());
        }
    }

    @Nested
    @DisplayName("User session invalidation")
    class SessionInvalidation {

        @Test
        @DisplayName("Increments permission version for users with the role")
        void invalidatesUserSessions() {
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();
            when(permissionRepository.findAllById(List.of(PERM_ID_TENANT))).thenReturn(List.of(tenantPermission));
            when(userRepository.findUserIdsByRoleId(ROLE_ID)).thenReturn(List.of(userId1, userId2));

            RolePermissionRequest request = buildRequest(List.of(PERM_ID_TENANT));
            rolePermissionService.assignPermissionsToRole(request);

            verify(userRepository).incrementPermissionVersion(userId1);
            verify(userRepository).incrementPermissionVersion(userId2);
            verify(permissionEvaluator).clearUserCache(userId1);
            verify(permissionEvaluator).clearUserCache(userId2);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Missing admin ID → BusinessRuleException")
        void noAdminId_throws() {
            when(currentUserResolver.getCurrentUserId()).thenReturn(null);

            RolePermissionRequest request = buildRequest(List.of(PERM_ID_TENANT));

            assertThrows(BusinessRuleException.class,
                    () -> rolePermissionService.assignPermissionsToRole(request));
        }

        @Test
        @DisplayName("Non-existent role → ResourceNotFoundException")
        void nonExistentRole_throws() {
            UUID badRoleId = UUID.randomUUID();
            when(roleRepository.findById(badRoleId)).thenReturn(Optional.empty());

            RolePermissionRequest request = new RolePermissionRequest();
            request.setRoleId(badRoleId);
            request.setPermissionIds(List.of(PERM_ID_TENANT));

            assertThrows(ResourceNotFoundException.class,
                    () -> rolePermissionService.assignPermissionsToRole(request));
        }
    }

    private RolePermissionRequest buildRequest(List<UUID> permissionIds) {
        RolePermissionRequest request = new RolePermissionRequest();
        request.setRoleId(ROLE_ID);
        request.setPermissionIds(permissionIds);
        return request;
    }
}
