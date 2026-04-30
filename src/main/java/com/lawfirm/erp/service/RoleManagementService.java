// service/RoleManagementService.java
package com.lawfirm.erp.service;

import com.lawfirm.erp.dto.admin.request.RoleRequest;
import com.lawfirm.erp.dto.admin.response.RoleResponse;
import com.lawfirm.erp.entity.Role;
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
public class RoleManagementService {

    private final RoleRepository roleRepository;

    @Transactional
    public RoleResponse upsertRole(RoleRequest request, Long adminId) {
        Role role;

        // Try to find existing role
        if (request.getId() != null && request.getId() > 0) {
            role = roleRepository.findById(request.getId())
                    .orElseThrow(() -> new RuntimeException("Role not found with id: " + request.getId()));
        } else if (request.getCode() != null && !request.getCode().isEmpty()) {
            role = roleRepository.findByRoleCode(request.getCode()).orElse(null);
        } else {
            role = null;
        }

        if (role != null) {
            // Update existing role
            if (role.getIsSystem()) {
                throw new RuntimeException("Cannot modify system role: " + role.getRoleName());
            }
            role.setRoleName(request.getName());
            role.setRoleCode(request.getCode());
            role.setDescription(request.getDescription());
            role.setModifiedBy(adminId);
            log.info("Role updated: {} by admin: {}", role.getRoleName(), adminId);
        } else {
            // Create new role
            if (roleRepository.existsByRoleName(request.getName())) {
                throw new RuntimeException("Role name already exists: " + request.getName());
            }
            if (roleRepository.existsByRoleCode(request.getCode())) {
                throw new RuntimeException("Role code already exists: " + request.getCode());
            }
            role = new Role();
            role.setRoleName(request.getName());
            role.setRoleCode(request.getCode());
            role.setDescription(request.getDescription());
            role.setIsSystem(false);
            role.setActive(true);
            role.setCreatedBy(adminId);
            log.info("Role created: {} by admin: {}", role.getRoleName(), adminId);
        }

        role = roleRepository.save(role);
        return toResponse(role);
    }

    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public RoleResponse getRoleById(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));
        return toResponse(role);
    }

    @Transactional
    public void deleteRole(Long roleId, Long adminId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));
        if (role.getIsSystem()) {
            throw new RuntimeException("Cannot delete system role: " + role.getRoleName());
        }
        roleRepository.delete(role);
        log.info("Role deleted: {} by admin: {}", role.getRoleName(), adminId);
    }

    @Transactional
    public RoleResponse toggleRoleStatus(Long roleId, Long adminId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));
        if (role.getIsSystem()) {
            throw new RuntimeException("Cannot toggle system role status");
        }
        role.setActive(!role.isActive());
        role.setModifiedBy(adminId);
        role = roleRepository.save(role);
        log.info("Role {} toggled to {} by admin: {}", role.getRoleName(), role.isActive(), adminId);
        return toResponse(role);
    }

    private RoleResponse toResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getRoleName())
                .code(role.getRoleCode())
                .description(role.getDescription())
                .isSystem(role.getIsSystem())
                .isActive(role.isActive())
                .createdAt(role.getCreatedDate())
                .build();
    }
}