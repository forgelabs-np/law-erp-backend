package com.lawfirm.erp.service;

import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.dto.admin.response.RolePermissionResponse;
import com.lawfirm.erp.entity.Permission;
import com.lawfirm.erp.entity.Role;
import com.lawfirm.erp.entity.RolePermission;
import com.lawfirm.erp.repository.PermissionRepository;
import com.lawfirm.erp.repository.RolePermissionRepository;
import com.lawfirm.erp.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RolePermissionService {

    private final RolePermissionRepository rolePermissionRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Transactional
    public void assignPermissionsToRole(RolePermissionRequest request, Long adminId) {
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found: " + request.getRoleId()));

        rolePermissionRepository.deleteByRoleId(request.getRoleId());

        for (Long permissionId : request.getPermissionIds()) {
            Permission permission = permissionRepository.findById(permissionId)
                    .orElseThrow(() -> new RuntimeException("Permission not found: " + permissionId));

            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(role.getId());
            rolePermission.setPermissionId(permission.getId());
            rolePermission.setCreatedBy(adminId);
            rolePermissionRepository.save(rolePermission);
        }

        log.info("Assigned {} permissions to role: {} by admin: {}",
                request.getPermissionIds().size(), role.getRoleName(), adminId);
    }

    public RolePermissionResponse getRolePermissions(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));

        List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleId(roleId);

        List<PermissionResponse> permissions = rolePermissions.stream()
                .map(rp -> permissionRepository.findById(rp.getPermissionId()))
                .filter(opt -> opt.isPresent())
                .map(opt -> PermissionResponse.builder()
                        .id(opt.get().getId())
                        .name(opt.get().getName())
                        .code(opt.get().getCode())
                        .description(opt.get().getDescription())
                        .isActive(opt.get().isActive())
                        .createdAt(opt.get().getCreatedDate())
                        .build())
                .collect(Collectors.toList());

        return RolePermissionResponse.builder()
                .roleId(role.getId())
                .roleName(role.getRoleName())
                .permissions(permissions)
                .build();
    }
}