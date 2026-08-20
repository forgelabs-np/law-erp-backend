package com.lawfirm.erp.modules.usermanagement.mapper;

import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.modules.audit.entity.AuditLog;
import com.lawfirm.erp.modules.usermanagement.dto.response.UserProfileResponse;
import com.lawfirm.erp.modules.usermanagement.dto.response.UserSummaryResponse;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class UserManagementMapper {

    public UserSummaryResponse toSummary(User user) {
        Role role = user.getRole();
        return UserSummaryResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mobileNo(user.getMobileNo())
                .userType(user.getUserType())
                .isActive(user.isActive())
                .roleId(role != null ? role.getId() : null)
                .roleName(role != null ? role.getRoleName() : null)
                .roleCode(role != null ? role.getRoleCode() : null)
                .portalAccessEnabled(user.getPortalAccessEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public UserProfileResponse.ActivityEntry toActivityEntry(AuditLog log) {
        return UserProfileResponse.ActivityEntry.builder()
                .action(log.getAction() != null ? log.getAction().name() : null)
                .entityType(log.getEntityType() != null ? log.getEntityType().name() : null)
                .entityId(log.getEntityId())
                .summary(log.getSummary())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }

    public List<UserProfileResponse.ModulePermissionGroup> groupByModule(List<Permission> permissions) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();

        for (Permission p : permissions) {
            if (p.getCode() == null || !p.getCode().contains(":")) continue;
            String[] parts = p.getCode().split(":", 2);
            grouped.computeIfAbsent(parts[0], k -> new ArrayList<>()).add(parts[1]);
        }

        return grouped.entrySet().stream()
                .map(e -> UserProfileResponse.ModulePermissionGroup.builder()
                        .moduleCode(e.getKey())
                        .moduleName(toHumanReadable(e.getKey()))
                        .actions(e.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    public String toHumanReadable(String code) {
        return java.util.Arrays.stream(code.split("_"))
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
