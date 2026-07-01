package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.dto.admin.response.RoleResponse;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirmRoleService {

    private final RoleRepository roleRepository;
    private final CurrentUserResolver currentUserResolver;

    /**
     * Returns ONLY firm-scoped roles (isSystem = false)
     * This filters out the global system role templates.
     * Firm Admin can only see and assign firm-scoped roles.
     */
    public List<RoleResponse> getFirmRoles() {
        UUID firmId = getCurrentFirmId();

        List<Role> firmRoles = roleRepository.findByFirmIdAndIsSystemFalse(firmId);

        return firmRoles.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private UUID getCurrentFirmId() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) {
            throw new ForbiddenException("This action requires a firm context");
        }
        return firmId;
    }

    private RoleResponse toResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getRoleName())
                .code(role.getRoleCode())
                .description(role.getDescription())
                .isSystem(role.getIsSystem())
                .isActive(role.isActive())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}