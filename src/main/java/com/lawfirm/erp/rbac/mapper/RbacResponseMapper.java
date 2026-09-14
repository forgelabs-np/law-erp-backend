package com.lawfirm.erp.rbac.mapper;

import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.dto.admin.response.RoleResponse;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RbacResponseMapper {

    public PermissionResponse toPermissionResponse(Permission entity) {
        if (entity == null) return null;
        return PermissionResponse.builder()
                .id(entity.getId())
                .action(entity.getAction())
                .scope(entity.getScope())
                .code(entity.getCode())
                .description(entity.getDescription())
                .isActive(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public List<PermissionResponse> toPermissionResponseList(List<Permission> permissions) {
        return permissions.stream()
                .map(this::toPermissionResponse)
                .collect(Collectors.toList());
    }

    public RoleResponse toRoleResponse(Role role, List<PermissionResponse> permissions) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getRoleName())
                .code(role.getRoleCode())
                .description(role.getDescription())
                .isSystem(role.getIsSystem() != null && role.getIsSystem())
                .isActive(role.isActive())
                .permissions(permissions)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    public RoleResponse toMinimalRoleResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getRoleName())
                .code(role.getRoleCode())
                .description(role.getDescription())
                .isSystem(role.getIsSystem() != null && role.getIsSystem())
                .isActive(role.isActive())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
