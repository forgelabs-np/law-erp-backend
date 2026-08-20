package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.PermissionAction;
import com.lawfirm.erp.common.enums.PermissionScope;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.DuplicateResourceException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.dto.admin.request.PermissionRequest;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.RolePermission;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionServiceTest {

    @Mock private PermissionRepository permissionRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private AuditService auditService;
    @Mock private com.lawfirm.erp.rbac.mapper.RbacResponseMapper rbacResponseMapper;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID SUPER_ADMIN_ROLE_ID = UUID.randomUUID();
    private static final UUID EXISTING_PERM_ID = UUID.randomUUID();
    private static final String MODULE_CODE = "USER_MANAGEMENT";
    private static final String PERMISSION_CODE = "USER_MANAGEMENT:VIEW";

    private Role superAdminRole;
    private Permission existingPermission;

    @BeforeEach
    void setUp() {
        superAdminRole = new Role();
        superAdminRole.setId(SUPER_ADMIN_ROLE_ID);
        superAdminRole.setRoleCode("SUPER_ADMIN");
        superAdminRole.setRoleName("SUPER_ADMIN");

        existingPermission = Permission.builder()
                .moduleCode(MODULE_CODE)
                .code(PERMISSION_CODE)
                .action(PermissionAction.VIEW)
                .scope(PermissionScope.TENANT)
                .build();
        existingPermission.setId(EXISTING_PERM_ID);
        existingPermission.setActive(true);

        when(currentUserResolver.getCurrentUserId()).thenReturn(ADMIN_ID);

        lenient().when(rbacResponseMapper.toPermissionResponse(any(Permission.class))).thenAnswer(i -> {
            Permission p = i.getArgument(0);
            return com.lawfirm.erp.dto.admin.response.PermissionResponse.builder()
                    .id(p.getId()).action(p.getAction()).scope(p.getScope())
                    .code(p.getCode()).description(p.getDescription())
                    .isActive(p.isActive()).createdAt(p.getCreatedAt()).build();
        });
        lenient().when(rbacResponseMapper.toPermissionResponseList(any())).thenAnswer(i -> {
            java.util.List<Permission> perms = i.getArgument(0);
            return perms.stream().map(p -> com.lawfirm.erp.dto.admin.response.PermissionResponse.builder()
                    .id(p.getId()).action(p.getAction()).scope(p.getScope())
                    .code(p.getCode()).description(p.getDescription())
                    .isActive(p.isActive()).createdAt(p.getCreatedAt()).build())
                    .collect(java.util.stream.Collectors.toList());
        });
    }

    @Nested
    @DisplayName("Permission creation")
    class CreatePermission {

        @Test
        @DisplayName("Creates permission and auto-assigns to SUPER_ADMIN")
        void create_autoAssignsToSuperAdmin() {
            PermissionRequest request = buildCreateRequest();
            when(permissionRepository.findById(any())).thenReturn(Optional.empty());
            when(permissionRepository.findByCode(PERMISSION_CODE)).thenReturn(Optional.empty());
            when(permissionRepository.existsByCode(PERMISSION_CODE)).thenReturn(false);
            when(permissionRepository.save(any(Permission.class))).thenAnswer(i -> {
                Permission p = i.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(roleRepository.findSystemRoleByCode("SUPER_ADMIN")).thenReturn(Optional.of(superAdminRole));
            when(rolePermissionRepository.findPermissionsByRoleId(SUPER_ADMIN_ROLE_ID)).thenReturn(Collections.emptyList());

            PermissionResponse response = permissionService.upsert(request);

            assertNotNull(response);
            assertEquals(PERMISSION_CODE, response.getCode());
            assertEquals(PermissionScope.TENANT, response.getScope());

            // Verify auto-assign: RolePermission saved
            verify(rolePermissionRepository).save(any(RolePermission.class));
            verify(auditService).log(eq(AuditAction.PERMISSION_CREATED), eq(AuditEntity.PERMISSION),
                    any(UUID.class), anyString());
        }

        @Test
        @DisplayName("Auto-assign skips if already assigned to SUPER_ADMIN")
        void autoAssign_skipsIfAlreadyAssigned() {
            PermissionRequest request = buildCreateRequest();
            when(permissionRepository.findById(any())).thenReturn(Optional.empty());
            when(permissionRepository.findByCode(PERMISSION_CODE)).thenReturn(Optional.empty());
            when(permissionRepository.existsByCode(PERMISSION_CODE)).thenReturn(false);
            when(permissionRepository.save(any(Permission.class))).thenAnswer(i -> {
                Permission p = i.getArgument(0);
                p.setId(EXISTING_PERM_ID);
                return p;
            });
            when(roleRepository.findSystemRoleByCode("SUPER_ADMIN")).thenReturn(Optional.of(superAdminRole));
            when(rolePermissionRepository.findPermissionsByRoleId(SUPER_ADMIN_ROLE_ID))
                    .thenReturn(List.of(existingPermission));

            permissionService.upsert(request);

            verify(rolePermissionRepository, never()).save(any(RolePermission.class));
        }

        @Test
        @DisplayName("Duplicate permission code → DuplicateResourceException")
        void duplicateCode_throws() {
            PermissionRequest request = buildCreateRequest();
            when(permissionRepository.findById(any())).thenReturn(Optional.empty());
            when(permissionRepository.findByCode(PERMISSION_CODE)).thenReturn(Optional.empty());
            when(permissionRepository.existsByCode(PERMISSION_CODE)).thenReturn(true);

            assertThrows(DuplicateResourceException.class,
                    () -> permissionService.upsert(request));
            verify(permissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Missing admin ID → BusinessRuleException")
        void noAdminId_throws() {
            when(currentUserResolver.getCurrentUserId()).thenReturn(null);

            assertThrows(BusinessRuleException.class,
                    () -> permissionService.upsert(buildCreateRequest()));
        }
    }

    @Nested
    @DisplayName("Permission update")
    class UpdatePermission {

        @Test
        @DisplayName("Updates existing permission, no auto-assign")
        void update_doesNotAutoAssign() {
            PermissionRequest request = buildUpdateRequest();
            when(permissionRepository.findById(EXISTING_PERM_ID)).thenReturn(Optional.of(existingPermission));
            when(permissionRepository.save(any(Permission.class))).thenReturn(existingPermission);

            PermissionResponse response = permissionService.upsert(request);

            assertNotNull(response);
            assertEquals(PermissionScope.TENANT, response.getScope());

            // Verify NO auto-assign on update
            verify(roleRepository, never()).findSystemRoleByCode(any());
            verify(rolePermissionRepository, never()).save(any(RolePermission.class));
            verify(auditService).log(eq(AuditAction.PERMISSION_UPDATED), eq(AuditEntity.PERMISSION),
                    eq(EXISTING_PERM_ID), anyString());
        }

        @Test
        @DisplayName("Update changes scope and action")
        void updateChangesFields() {
            PermissionRequest request = buildUpdateRequest();
            request.setScope(PermissionScope.GLOBAL);
            request.setAction(PermissionAction.ACCESS);
            request.setDescription("Updated description");

            when(permissionRepository.findById(EXISTING_PERM_ID)).thenReturn(Optional.of(existingPermission));
            when(permissionRepository.save(any(Permission.class))).thenAnswer(i -> i.getArgument(0));

            PermissionResponse response = permissionService.upsert(request);

            assertEquals(PermissionScope.GLOBAL, response.getScope());
        }
    }

    @Nested
    @DisplayName("Permission deletion")
    class DeletePermission {

        @Test
        @DisplayName("Cannot delete permission assigned to roles")
        void assignedToRole_throws() {
            when(permissionRepository.findById(EXISTING_PERM_ID)).thenReturn(Optional.of(existingPermission));
            when(permissionRepository.countRolePermissionsByPermissionId(EXISTING_PERM_ID)).thenReturn(3L);

            assertThrows(BusinessRuleException.class,
                    () -> permissionService.delete(EXISTING_PERM_ID));
            verify(permissionRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Cannot delete permission assigned to modules")
        void assignedToModule_throws() {
            when(permissionRepository.findById(EXISTING_PERM_ID)).thenReturn(Optional.of(existingPermission));
            when(permissionRepository.countRolePermissionsByPermissionId(EXISTING_PERM_ID)).thenReturn(0L);
            when(permissionRepository.countModulePermissionsByPermissionId(EXISTING_PERM_ID)).thenReturn(2L);

            assertThrows(BusinessRuleException.class,
                    () -> permissionService.delete(EXISTING_PERM_ID));
            verify(permissionRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Unassigned permission → delete succeeds")
        void unassigned_deletes() {
            when(permissionRepository.findById(EXISTING_PERM_ID)).thenReturn(Optional.of(existingPermission));
            when(permissionRepository.countRolePermissionsByPermissionId(EXISTING_PERM_ID)).thenReturn(0L);
            when(permissionRepository.countModulePermissionsByPermissionId(EXISTING_PERM_ID)).thenReturn(0L);

            permissionService.delete(EXISTING_PERM_ID);

            verify(permissionRepository).delete(existingPermission);
            verify(auditService).log(eq(AuditAction.PERMISSION_DELETED), eq(AuditEntity.PERMISSION),
                    eq(EXISTING_PERM_ID), anyString());
        }

        @Test
        @DisplayName("Non-existent permission ID → ResourceNotFoundException")
        void nonExistent_throws() {
            UUID badId = UUID.randomUUID();
            when(permissionRepository.findById(badId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> permissionService.delete(badId));
        }
    }

    @Nested
    @DisplayName("Permission listing")
    class ListPermissions {

        @Test
        @DisplayName("findAll returns all permissions")
        void findAll() {
            when(permissionRepository.findAll()).thenReturn(List.of(existingPermission));

            List<PermissionResponse> responses = permissionService.findAll();

            assertEquals(1, responses.size());
            assertEquals(PERMISSION_CODE, responses.get(0).getCode());
        }

        @Test
        @DisplayName("findActive filters inactive permissions")
        void findActive() {
            Permission inactive = Permission.builder()
                    .code("INACTIVE:ACCESS")
                    .action(PermissionAction.ACCESS)
                    .scope(PermissionScope.TENANT)
                    .build();
            inactive.setId(UUID.randomUUID());
            inactive.setActive(false);

            when(permissionRepository.findAll()).thenReturn(List.of(existingPermission, inactive));

            List<PermissionResponse> responses = permissionService.findActive();

            assertEquals(1, responses.size());
            assertEquals(PERMISSION_CODE, responses.get(0).getCode());
        }
    }

    @Nested
    @DisplayName("Permission toggle status")
    class ToggleStatus {

        @Test
        @DisplayName("Toggle flips isActive")
        void toggleFlipsStatus() {
            when(permissionRepository.findById(EXISTING_PERM_ID)).thenReturn(Optional.of(existingPermission));
            when(permissionRepository.save(any(Permission.class))).thenAnswer(i -> i.getArgument(0));

            existingPermission.setActive(true);
            PermissionResponse response = permissionService.toggleStatus(EXISTING_PERM_ID);

            assertFalse(response.getIsActive());
            verify(auditService).log(eq(AuditAction.PERMISSION_DEACTIVATED), eq(AuditEntity.PERMISSION),
                    eq(EXISTING_PERM_ID), anyString());

            when(permissionRepository.findById(EXISTING_PERM_ID)).thenReturn(Optional.of(existingPermission));
            response = permissionService.toggleStatus(EXISTING_PERM_ID);

            assertTrue(response.getIsActive());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private PermissionRequest buildCreateRequest() {
        PermissionRequest request = new PermissionRequest();
        request.setModuleCode(MODULE_CODE);
        request.setCode(PERMISSION_CODE);
        request.setAction(PermissionAction.VIEW);
        request.setScope(PermissionScope.TENANT);
        request.setDescription("View user management");
        return request;
    }

    private PermissionRequest buildUpdateRequest() {
        PermissionRequest request = new PermissionRequest();
        request.setId(EXISTING_PERM_ID);
        request.setModuleCode(MODULE_CODE);
        request.setCode(PERMISSION_CODE);
        request.setAction(PermissionAction.VIEW);
        request.setScope(PermissionScope.TENANT);
        request.setDescription("Updated description");
        return request;
    }
}
