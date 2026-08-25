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
public class RolePermissionServiceImpl implements RolePermissionService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CurrentUserResolver currentUserResolver;
    private final UserRepository userRepository;
    private final PermissionEvaluator permissionEvaluator;
    private final com.lawfirm.erp.rbac.mapper.RbacResponseMapper rbacResponseMapper;

    @Transactional
    @Override
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

        // Verify all permissions exist
        List<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds());
        if (permissions.size() != request.getPermissionIds().size()) {
            throw new ResourceNotFoundException("One or more permission IDs are invalid");
        }

        // ── Ceiling check ─────────────────────────────────────────────────
        // A custom role may only hold permissions that its parent system role
        // has. GLOBAL-scope permissions are reserved for SUPER_ADMIN.
        if (role.getParentRoleId() != null) {
            Role parentRole = roleRepository.findById(role.getParentRoleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent system role not found"));

            Set<UUID> parentPermIds = rolePermissionRepository.findPermissionsByRoleId(parentRole.getId())
                    .stream().map(Permission::getId).collect(Collectors.toSet());

            for (Permission perm : permissions) {
                if (!parentPermIds.contains(perm.getId()) || perm.getScope() == PermissionScope.GLOBAL) {
                    throw new ForbiddenException(
                            "Permission '" + perm.getCode() + "' (scope: " + perm.getScope()
                            + ") is not allowed for role type '" + parentRole.getRoleCode()
                            + "'. Exceeds system ceiling."
                    );
                }
            }
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

    @Override
    public RolePermissionResponse getRolePermissions(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Role not found with id: %s", roleId)
                ));

        List<Permission> permissions = rolePermissionRepository.findPermissionsByRoleId(roleId);

        List<PermissionResponse> permissionResponses = rbacResponseMapper.toPermissionResponseList(permissions);

        return RolePermissionResponse.builder()
                .roleId(role.getId())
                .roleName(role.getRoleName())
                .roleCode(role.getRoleCode())
                .permissions(permissionResponses)
                .build();
    }



}