package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.enums.PermissionScope;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.dto.admin.response.RolePermissionResponse;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.RolePermission;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.PermissionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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
    private final UserRepository userRepository;
    private final PermissionEvaluator permissionEvaluator;

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

        if (role.getIsSystem() != null && role.getIsSystem()) {
            throw new BusinessRuleException(
                    String.format("Cannot modify permissions for system role: %s", role.getRoleName())
            );
        }

        if (role.getParentRoleId() != null) {
            Role parentRole = roleRepository.findById(role.getParentRoleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent system role not found"));

            PermissionScope maxScope = getMaxScopeForParent(parentRole.getRoleCode());

            for (UUID permId : request.getPermissionIds()) {
                Permission perm = permissionRepository.findById(permId)
                        .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permId));
                if (!isScopeAllowed(perm.getScope(), maxScope)) {
                    throw new ForbiddenException(
                            "Permission '" + perm.getCode() + "' (scope: " + perm.getScope()
                            + ") exceeds your role's ceiling. This scope is not available for role type '"
                            + parentRole.getRoleCode() + "'."
                    );
                }
            }
        }

        // Verify all permissions exist
        List<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds());
        if (permissions.size() != request.getPermissionIds().size()) {
            throw new ResourceNotFoundException("One or more permission IDs are invalid");
        }

        // Remove existing permissions
        rolePermissionRepository.deleteByRoleId(role.getId());

        // Assign new permissions
        List<RolePermission> rolePermissions = permissions.stream()
                .map(permission -> RolePermission.builder()
                        .role(role)
                        .permission(permission)
                        .build())
                .collect(Collectors.toList());

        rolePermissions.forEach(rp -> {
            rp.setCreatedBy(adminId);
            rp.setCreatedAt(LocalDateTime.now());
        });

        rolePermissionRepository.saveAll(rolePermissions);

        // Invalidate user sessions
        List<UUID> userIds = userRepository.findUserIdsByRoleId(role.getId());
        for (UUID userId : userIds) {
            userRepository.incrementPermissionVersion(userId);
            permissionEvaluator.clearUserCache(userId);
        }

        log.info("Assigned {} permissions to role: {} by admin: {}. Invalidated {} user sessions.",
                permissions.size(), role.getRoleCode(), adminId, userIds.size());
    }

    public RolePermissionResponse getRolePermissions(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Role not found with id: %s", roleId)
                ));

        List<Permission> permissions = rolePermissionRepository.findPermissionsByRoleId(roleId);

        List<PermissionResponse> permissionResponses = permissions.stream()
                .map(this::convertPermissionToResponse)
                .collect(Collectors.toList());

        return RolePermissionResponse.builder()
                .roleId(role.getId())
                .roleName(role.getRoleName())
                .roleCode(role.getRoleCode())
                .permissions(permissionResponses)
                .build();
    }

    private PermissionResponse convertPermissionToResponse(Permission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .action(permission.getAction())
                .scope(permission.getScope())
                .code(permission.getCode())
                .description(permission.getDescription())
                .isActive(permission.isActive())
                .createdAt(permission.getCreatedAt())
                .build();
    }

    private static PermissionScope getMaxScopeForParent(String roleCode) {
        return switch (roleCode) {
            case "SUPER_ADMIN" -> PermissionScope.GLOBAL;
            case "FIRM_ADMIN"  -> PermissionScope.TENANT;
            case "ADVOCATE", "PARALEGAL" -> PermissionScope.ASSIGNED;
            case "CLIENT"      -> PermissionScope.OWN;
            default            -> PermissionScope.OWN;
        };
    }

    private static boolean isScopeAllowed(PermissionScope permScope, PermissionScope maxScope) {
        if (maxScope == PermissionScope.GLOBAL) return true;
        if (maxScope == PermissionScope.TENANT) return permScope != PermissionScope.GLOBAL;
        if (maxScope == PermissionScope.ASSIGNED)
            return permScope == PermissionScope.ASSIGNED || permScope == PermissionScope.OWN;
        return permScope == PermissionScope.OWN;
    }
}