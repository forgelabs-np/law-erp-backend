package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.DuplicateResourceException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.dto.admin.request.RoleRequest;
import com.lawfirm.erp.dto.admin.response.ModuleResponse;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.dto.admin.response.RoleResponse;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleManagementService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CurrentUserResolver currentUserResolver;

    @Transactional
    public RoleResponse upsertRole(RoleRequest request) {
        UUID adminId = getCurrentAdminId();

        Role role = findExistingRole(request);

        if (role != null) {
            validateNotSystemRole(role, "modify");
            updateRole(role, request, adminId);
            log.info("Role updated: {} by admin: {}", role.getRoleCode(), adminId);
        } else {
            validateNoDuplicateRole(request);
            role = createRole(request, adminId);
            log.info("Role created: {} by admin: {}", role.getRoleCode(), adminId);
        }

        role = roleRepository.save(role);
        return convertToCompleteResponse(role);
    }

    private UUID getCurrentAdminId() {
        UUID adminId = currentUserResolver.getCurrentUserId();
        if (adminId == null) {
            throw new BusinessRuleException("Authenticated user not found");
        }
        return adminId;
    }

    private Role findExistingRole(RoleRequest request) {
        if (request.getId() != null) {
            return roleRepository.findById(request.getId()).orElse(null);
        }
        if (request.getCode() != null && !request.getCode().isEmpty()) {
            return roleRepository.findByRoleCode(request.getCode()).orElse(null);
        }
        return null;
    }

    private void validateNotSystemRole(Role role, String operation) {
        if (role.getIsSystem() != null && role.getIsSystem()) {
            throw new BusinessRuleException(
                    String.format("Cannot %s system role: %s", operation, role.getRoleName())
            );
        }
    }

    private void validateNoDuplicateRole(RoleRequest request) {
        if (roleRepository.existsByRoleName(request.getName())) {
            throw new DuplicateResourceException(
                    String.format("Role name already exists: %s", request.getName())
            );
        }
        if (roleRepository.existsByRoleCode(request.getCode())) {
            throw new DuplicateResourceException(
                    String.format("Role code already exists: %s", request.getCode())
            );
        }
    }

    private void updateRole(Role role, RoleRequest request, UUID adminId) {
        role.setRoleName(request.getName());
        role.setRoleCode(request.getCode());
        role.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            role.setActive(request.getIsActive());
        }
        role.setUpdatedBy(adminId);
        role.setUpdatedAt(LocalDateTime.now());
    }

    private Role createRole(RoleRequest request, UUID adminId) {
        Role role = new Role();
        role.setRoleName(request.getName());
        role.setRoleCode(request.getCode());
        role.setDescription(request.getDescription());
        role.setIsSystem(false);
        role.setActive(request.getIsActive() != null ? request.getIsActive() : true);
        role.setCreatedBy(adminId);
        role.setCreatedAt(LocalDateTime.now());
        return role;
    }

    // GET ALL - Minimal data (no permissions)
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::convertToMinimalResponse)
                .collect(Collectors.toList());
    }

    // GET ACTIVE - Minimal data
    public List<RoleResponse> getActiveRoles() {
        return roleRepository.findAll().stream()
                .filter(Role::isActive)
                .map(this::convertToMinimalResponse)
                .collect(Collectors.toList());
    }

    // GET BY ID - Complete data (with permissions)
    public RoleResponse getRoleById(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Role not found with id: %s", roleId)
                ));
        return convertToCompleteResponse(role);
    }

    @Transactional
    public void deleteRole(UUID roleId) {
        UUID adminId = getCurrentAdminId();

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Role not found with id: %s", roleId)
                ));

        validateNotSystemRole(role, "delete");

        if (roleRepository.countUsersByRoleId(roleId) > 0) {
            throw new BusinessRuleException(
                    String.format("Cannot delete role: %s. It is currently assigned to %d user(s)",
                            role.getRoleName(), roleRepository.countUsersByRoleId(roleId))
            );
        }

        roleRepository.delete(role);
        log.info("Role deleted: {} by admin: {}", role.getRoleCode(), adminId);
    }

    @Transactional
    public RoleResponse toggleRoleStatus(UUID roleId) {
        UUID adminId = getCurrentAdminId();

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Role not found with id: %s", roleId)
                ));

        validateNotSystemRole(role, "toggle status of");

        role.setActive(!role.isActive());
        role.setUpdatedBy(adminId);
        role.setUpdatedAt(LocalDateTime.now());
        role = roleRepository.save(role);

        log.info("Role {} toggled to {} by admin: {}",
                role.getRoleCode(), role.isActive(), adminId);

        return convertToCompleteResponse(role);
    }

    // MINIMAL response (no permissions) - for getAll, getActive
    private RoleResponse convertToMinimalResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getRoleName())
                .code(role.getRoleCode())
                .description(role.getDescription())
                .isSystem(role.getIsSystem() != null && role.getIsSystem())
                .isActive(role.isActive())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                // permissions = null (not included)
                .build();
    }

    // COMPLETE response (with permissions) - for getById, upsert, toggle
    private RoleResponse convertToCompleteResponse(Role role) {
        List<PermissionResponse> permissions = rolePermissionRepository
                .findPermissionsByRoleId(role.getId())
                .stream()
                .map(this::convertPermissionToResponse)
                .collect(Collectors.toList());

        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getRoleName())
                .code(role.getRoleCode())
                .description(role.getDescription())
                .isSystem(role.getIsSystem() != null && role.getIsSystem())
                .isActive(role.isActive())
                .permissions(permissions)  // Only included in complete response
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    private PermissionResponse convertPermissionToResponse(Permission permission) {
        // For permission's module, send minimal data (avoid deep nesting)
        ModuleResponse moduleResponse = ModuleResponse.builder()
                .id(permission.getModule().getId())
                .name(permission.getModule().getName())
                .code(permission.getModule().getCode())
                .isActive(permission.getModule().isActive())
                .build();

        return PermissionResponse.builder()
                .id(permission.getId())
                .module(moduleResponse)
                .action(permission.getAction())
                .code(permission.getCode())
                .description(permission.getDescription())
                .isActive(permission.isActive())
                .createdAt(permission.getCreatedAt())
                .build();
    }
}