package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.DuplicateResourceException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.dto.admin.request.PermissionRequest;
import com.lawfirm.erp.dto.admin.response.GroupedPermissionResponse;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.rbac.entity.Module;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.RolePermission;
import com.lawfirm.erp.rbac.repository.ModulePermissionRepository;
import com.lawfirm.erp.rbac.repository.ModuleRepository;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;
    private final ModuleRepository moduleRepository;
    private final ModulePermissionRepository modulePermissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CurrentUserResolver currentUserResolver;
    private final AuditService auditService;
    private final com.lawfirm.erp.rbac.mapper.RbacResponseMapper rbacResponseMapper;

    @Transactional
    @Override
    public PermissionResponse upsert(PermissionRequest request) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        if (adminId == null) {
            throw new BusinessRuleException("Authenticated user not found");
        }

        String generatedCode = request.getCode();
        if (generatedCode == null || generatedCode.isEmpty()) {
            generatedCode = request.getModuleCode() + ":" + request.getAction().name();
        }

        Permission permission = findExistingPermission(request);

        if (permission != null) {
            updatePermission(permission, request, adminId);
            log.info("Permission updated: {} by admin: {}", permission.getCode(), adminId);
            permission = permissionRepository.save(permission);

            // ✅ AUDIT: Permission updated
            auditService.log(
                    AuditAction.PERMISSION_UPDATED,
                    AuditEntity.PERMISSION,
                    permission.getId(),
                    "Permission updated: " + permission.getCode()
            );
        } else {
            validateCodeUniqueness(generatedCode);
            permission = createPermission(request, generatedCode, adminId);
            log.info("Permission created: {} by admin: {}", permission.getCode(), adminId);
            permission = permissionRepository.save(permission);
            assignToSystemRoles(permission);

            // ✅ AUDIT: Permission created
            auditService.log(
                    AuditAction.PERMISSION_CREATED,
                    AuditEntity.PERMISSION,
                    permission.getId(),
                    "Permission created: " + permission.getCode()
            );
        }

        return toResponse(permission);
    }

    @Transactional
    @Override
    public void delete(UUID id) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));

        if (permissionRepository.countModulePermissionsByPermissionId(id) > 0) {
            throw new BusinessRuleException(
                    String.format("Cannot delete permission: %s. It is assigned to modules.", permission.getCode())
            );
        }

        if (permissionRepository.countRolePermissionsByPermissionId(id) > 0) {
            throw new BusinessRuleException(
                    String.format("Cannot delete permission: %s. It is assigned to roles.", permission.getCode())
            );
        }

        permissionRepository.delete(permission);
        log.info("Permission deleted: {} by admin: {}", permission.getCode(), adminId);

        // ✅ AUDIT: Permission deleted
        auditService.log(
                AuditAction.PERMISSION_DELETED,
                AuditEntity.PERMISSION,
                permission.getId(),
                "Permission deleted: " + permission.getCode()
        );
    }

    @Transactional
    @Override
    public PermissionResponse toggleStatus(UUID id) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));
        permission.setActive(!permission.isActive());
        permission.setUpdatedBy(adminId);
        permission.setUpdatedAt(LocalDateTime.now());
        permission = permissionRepository.save(permission);
        log.info("Permission {} toggled to {} by admin: {}",
                permission.getCode(), permission.isActive(), adminId);

        // ✅ AUDIT: Permission status toggled
        auditService.log(
                permission.isActive() ? AuditAction.PERMISSION_ACTIVATED : AuditAction.PERMISSION_DEACTIVATED,
                AuditEntity.PERMISSION,
                permission.getId(),
                "Permission " + permission.getCode() + " toggled to: " + (permission.isActive() ? "active" : "inactive")
        );

        return toResponse(permission);
    }

    @Override
    public List<PermissionResponse> findAll() {
        return permissionRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PermissionResponse> findActive() {
        return permissionRepository.findAllActive().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PermissionResponse findById(UUID id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));
        return toResponse(permission);
    }

    @Override
    public GroupedPermissionResponse findAllGroupedByModule() {
        List<Module> modules = moduleRepository.findAllWithParentOrderByDisplayOrder();

        // Batch-load all module-permission mappings in one query (eliminates N+1)
        List<UUID> moduleIds = modules.stream().map(Module::getId).collect(Collectors.toList());
        List<com.lawfirm.erp.rbac.entity.ModulePermission> allMappings = moduleIds.isEmpty()
                ? List.of()
                : modulePermissionRepository.findByModuleIdIn(moduleIds);

        // Group permissions by module ID
        Map<UUID, List<Permission>> permsByModule = allMappings.stream()
                .collect(Collectors.groupingBy(
                        mp -> mp.getModule().getId(),
                        Collectors.mapping(com.lawfirm.erp.rbac.entity.ModulePermission::getPermission, Collectors.toList())
                ));

        List<GroupedPermissionResponse.ModulePermissions> modulePermList = modules.stream()
                .map(module -> {
                    List<Permission> perms = permsByModule.getOrDefault(module.getId(), List.of());
                    List<PermissionResponse> permResponses = perms.stream()
                            .map(this::toResponse)
                            .collect(Collectors.toList());

                    return GroupedPermissionResponse.ModulePermissions.builder()
                            .moduleCode(module.getCode())
                            .moduleName(module.getName())
                            .moduleDescription(module.getDescription())
                            .icon(module.getIcon())
                            .path(module.getPath())
                            .displayOrder(module.getDisplayOrder())
                            .permissions(permResponses)
                            .build();
                })
                .collect(Collectors.toList());

        return GroupedPermissionResponse.builder()
                .modules(modulePermList)
                .build();
    }

    // ─── Private Methods ─────────────────────────────────────────────────────

    private Permission findExistingPermission(PermissionRequest request) {
        if (request.getId() != null) {
            return permissionRepository.findById(request.getId()).orElse(null);
        }
        if (request.getCode() != null && !request.getCode().isEmpty()) {
            return permissionRepository.findByCode(request.getCode()).orElse(null);
        }
        return null;
    }

    private void validateCodeUniqueness(String code) {
        if (permissionRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Permission code already exists: " + code);
        }
    }

    private void updatePermission(Permission permission, PermissionRequest request, UUID adminId) {
        permission.setAction(request.getAction());
        permission.setScope(request.getScope());
        permission.setCode(request.getCode());
        permission.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            permission.setActive(request.getIsActive());
        }
        permission.setUpdatedBy(adminId);
        permission.setUpdatedAt(LocalDateTime.now());
    }

    private Permission createPermission(PermissionRequest request, String code, UUID adminId) {
        Permission permission = new Permission();
        permission.setModuleCode(request.getModuleCode());
        permission.setAction(request.getAction());
        permission.setScope(request.getScope());
        permission.setCode(code);
        permission.setDescription(request.getDescription());
        permission.setActive(request.getIsActive() != null ? request.getIsActive() : true);
        permission.setCreatedBy(adminId);
        permission.setCreatedAt(LocalDateTime.now());
        return permission;
    }

    private void assignToSystemRoles(Permission permission) {
        roleRepository.findSystemRoleByCode("SUPER_ADMIN").ifPresent(superAdmin -> {
            boolean alreadyAssigned = rolePermissionRepository.findPermissionsByRoleId(superAdmin.getId())
                    .stream().anyMatch(p -> p.getId().equals(permission.getId()));
            if (!alreadyAssigned) {
                RolePermission rp = new RolePermission();
                rp.setRole(superAdmin);
                rp.setPermission(permission);
                rolePermissionRepository.save(rp);
                log.info("  Auto-assigned {} -> SUPER_ADMIN", permission.getCode());
            }
        });
    }

    private PermissionResponse toResponse(Permission entity) {
        return rbacResponseMapper.toPermissionResponse(entity);
    }
}