package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.dto.admin.response.RolePermissionResponse;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.RolePermission;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RolePermissionService {

    private final RolePermissionRepository rolePermissionRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Transactional
    public void assignPermissionsToRole(RolePermissionRequest request, UUID adminId) {
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found: " + request.getRoleId()));

        List<RolePermission> existing = rolePermissionRepository.findByRole(role);
        rolePermissionRepository.deleteAll(existing);

        for (UUID permissionId : request.getPermissionIds()) {
            Permission permission = permissionRepository.findById(permissionId)
                    .orElseThrow(() -> new RuntimeException("Permission not found: " + permissionId));

            RolePermission rolePermission = RolePermission.builder()
                    .role(role)
                    .permission(permission)
                    .build();
            rolePermission.setCreatedBy(adminId);
            rolePermissionRepository.save(rolePermission);
        }

        log.info("Assigned {} permissions to role: {} by admin: {}",
                request.getPermissionIds().size(), role.getRoleName(), adminId);
    }

    public RolePermissionResponse getRolePermissions(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));

        List<RolePermission> rolePermissions = rolePermissionRepository.findByRole(role);

        List<PermissionResponse> permissions = rolePermissions.stream()
                .map(RolePermission::getPermission)
                .map(perm -> PermissionResponse.builder()
                        .id(perm.getId())
                        .code(perm.getCode())
                        .module(perm.getModule().name())
                        .action(perm.getAction().name())
                        .description(perm.getDescription())
                        .isActive(perm.isActive())
                        .createdAt(perm.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return RolePermissionResponse.builder()
                .roleId(role.getId())
                .roleName(role.getRoleName())
                .permissions(permissions)
                .build();
    }
}