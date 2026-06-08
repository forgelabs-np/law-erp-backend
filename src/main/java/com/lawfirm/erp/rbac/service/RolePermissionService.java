package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.dto.admin.response.RolePermissionResponse;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.RolePermission;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
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
public class RolePermissionService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CurrentUserResolver currentUserResolver;

    @Transactional
    public void assignPermissionsToRole(RolePermissionRequest request) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        if (adminId == null) {
            throw new BusinessRuleException("Authenticated user not found");
        }

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Role not found with id: %s", request.getRoleId())
                ));

        // Prevent modifying system roles
        if (role.getIsSystem() != null && role.getIsSystem()) {
            throw new BusinessRuleException(
                    String.format("Cannot modify permissions for system role: %s", role.getRoleName())
            );
        }

        // Verify all permissions exist
        List<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds());
        if (permissions.size() != request.getPermissionIds().size()) {
            throw new ResourceNotFoundException("One or more permission IDs are invalid");
        }

        // Remove existing permissions
        rolePermissionRepository.deleteByRole(role);

        // Assign new permissions - FIXED: Use entity objects instead of IDs
        List<RolePermission> rolePermissions = permissions.stream()
                .map(permission -> RolePermission.builder()
                        .role(role)           // Set the Role entity
                        .permission(permission) // Set the Permission entity
                        .build())
                .collect(Collectors.toList());

        // Set audit fields manually if needed
        rolePermissions.forEach(rp -> {
            rp.setCreatedBy(adminId);
            rp.setCreatedAt(LocalDateTime.now());
        });

        rolePermissionRepository.saveAll(rolePermissions);

        log.info("Assigned {} permissions to role: {} by admin: {}",
                permissions.size(), role.getRoleCode(), adminId);
    }

    public RolePermissionResponse getRolePermissions(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Role not found with id: %s", roleId)
                ));

        List<Permission> permissions = rolePermissionRepository.findPermissionsByRoleId(roleId);

        List<PermissionResponse> permissionResponses = permissions.stream()
                .map(permission -> PermissionResponse.builder()
                        .id(permission.getId())
                        .module(permission.getModule())
                        .action(permission.getAction())
                        .code(permission.getCode())
                        .description(permission.getDescription())
                        .isActive(permission.isActive())
                        .createdAt(permission.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return RolePermissionResponse.builder()
                .roleId(role.getId())
                .roleName(role.getRoleName())
                .roleCode(role.getRoleCode())
                .permissions(permissionResponses)
                .build();
    }
}