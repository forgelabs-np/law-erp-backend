package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.common.constant.RoleCode;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.DuplicateResourceException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.request.RoleRequest;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.dto.admin.response.RoleResponse;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.RolePermission;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleManagementServiceImpl implements RoleManagementService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RolePermissionService rolePermissionService;
    private final CurrentUserResolver currentUserResolver;
    private final AuditService auditService;
    private final com.lawfirm.erp.rbac.mapper.RbacResponseMapper rbacResponseMapper;

    @Transactional
    @Override
    public RoleResponse upsertRole(RoleRequest request) {
        UUID adminId = getCurrentAdminId();

        Role role = findExistingRole(request);

        if (role != null) {
            validateNotSystemRole(role, "modify");
            updateRole(role, request, adminId);
            log.info("Role updated: {} by admin: {}", role.getRoleCode(), adminId);
            role = roleRepository.save(role);

            // ✅ AUDIT: Role updated
            auditService.log(
                    AuditAction.ROLE_UPDATED,
                    AuditEntity.ROLE,
                    role.getId(),
                    "Role updated: " + role.getRoleCode() + " (" + role.getRoleName() + ")"
            );
        } else {
            validateNoDuplicateRole(request);
            role = createRole(request, adminId);
            log.info("Role created: {} by admin: {}", role.getRoleCode(), adminId);
            role = roleRepository.save(role);

            //  AUDIT: Role created
            auditService.log(
                    AuditAction.ROLE_CREATED,
                    AuditEntity.ROLE,
                    role.getId(),
                    "Role created: " + role.getRoleCode() + " (" + role.getRoleName() + ")"
            );
        }

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            RolePermissionRequest permRequest = new RolePermissionRequest();
            permRequest.setRoleId(role.getId());
            permRequest.setPermissionIds(request.getPermissionIds());
            rolePermissionService.assignPermissionsToRole(permRequest);
        }

        return convertToCompleteResponse(role);
    }

    @Transactional
    @Override
    public void deleteRole(UUID roleId) {
        UUID adminId = getCurrentAdminId();

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Role not found with id: %s", roleId)
                ));

        validateNotSystemRole(role, "delete");

        int userCount = roleRepository.countUsersByRoleId(roleId);
        if (userCount > 0) {
            throw new BusinessRuleException(
                    String.format("Cannot delete role: %s. It is currently assigned to %d user(s)",
                            role.getRoleName(), userCount)
            );
        }

        roleRepository.delete(role);
        log.info("Role deleted: {} by admin: {}", role.getRoleCode(), adminId);

        //  AUDIT: Role deleted
        auditService.log(
                AuditAction.ROLE_DELETED,
                AuditEntity.ROLE,
                role.getId(),
                "Role deleted: " + role.getRoleCode()
        );
    }

    @Transactional
    @Override
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

        //  AUDIT: Role status toggled
        auditService.log(
                role.isActive() ? AuditAction.ROLE_ACTIVATED : AuditAction.ROLE_DEACTIVATED,
                AuditEntity.ROLE,
                role.getId(),
                "Role " + role.getRoleCode() + " toggled to: " + (role.isActive() ? "active" : "inactive")
        );

        return convertToCompleteResponse(role);
    }

    // ─── Private Methods ─────────────────────────────────────────────────────

    private UUID getCurrentAdminId() {
        UUID adminId = currentUserResolver.getCurrentUserId();
        if (adminId == null) {
            throw new BusinessRuleException("Authenticated user not found");
        }
        return adminId;
    }

    /** FIX: Use findAllByRoleCode with code lookup, then filter non-system roles.
     *  Previously used findSystemRoleByCode() which only found system roles (firm IS NULL),
     *  making the update-by-code path always fail for non-system roles.
     *  Now uses findAllByRoleCode() and filters for non-system roles. */
    private Role findExistingRole(RoleRequest request) {
        if (request.getId() != null) {
            return roleRepository.findById(request.getId()).orElse(null);
        }
        if (request.getCode() != null && !request.getCode().isEmpty()) {
            List<Role> roles = roleRepository.findAllByRoleCode(request.getCode());
            // Prefer non-system (firm-scoped) roles for update
            // System roles can't be modified (will be caught by validateNotSystemRole)
            return roles.stream()
                    .filter(r -> !Boolean.TRUE.equals(r.getIsSystem()))
                    .findFirst()
                    .orElse(null);
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
        role.setParentRoleId(resolveParentRoleId(request.getParentRoleId()));
        role.setActive(request.getIsActive() != null ? request.getIsActive() : true);
        role.setCreatedBy(adminId);
        role.setCreatedAt(LocalDateTime.now());
        return role;
    }

    /**
     * Resolves the base system template for a new custom role.
     * Null = no anchor (legal; ceiling then comes from the live FIRM_ADMIN branch).
     * Provided = must be an active system template and never SUPER_ADMIN.
     */
    private UUID resolveParentRoleId(UUID requestedParentRoleId) {
        if (requestedParentRoleId == null) {
            return null;
        }
        Role parent = roleRepository.findById(requestedParentRoleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parent role not found: " + requestedParentRoleId));
        if (!Boolean.TRUE.equals(parent.getIsSystem()) || parent.getFirm() != null) {
            throw new BusinessRuleException("Parent role must be a system template");
        }
        if (RoleCode.SUPER_ADMIN.equals(parent.getRoleCode())) {
            throw new BusinessRuleException("SUPER_ADMIN cannot be used as a base template");
        }
        if (!parent.isActive()) {
            throw new BusinessRuleException("Parent role template is not active");
        }
        return parent.getId();
    }

    @Override
    public List<RoleResponse> getAllRoles() {
        List<Role> roles = roleRepository.findAll();
        return batchConvertToCompleteResponse(roles);
    }

    @Override
    public List<RoleResponse> getActiveRoles() {
        List<Role> roles = roleRepository.findAllActive();
        return batchConvertToCompleteResponse(roles);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getSystemTemplates() {
        List<Role> templates = roleRepository.findByFirmIsNullAndIsSystemTrue();
        return batchConvertToCompleteResponse(templates);
    }

    /**
     * Batch-load permissions for all roles in one query, then build responses.
     * Eliminates the N+1 that was: 1 query for roles + N queries for permissions.
     */
    private List<RoleResponse> batchConvertToCompleteResponse(List<Role> roles) {
        if (roles.isEmpty()) return List.of();

        List<UUID> roleIds = roles.stream().map(Role::getId).collect(Collectors.toList());
        List<RolePermission> allRPs = rolePermissionRepository.findByRoleIdIn(roleIds);

        // Group permissions by role ID (single pass, no extra queries)
        Map<UUID, List<PermissionResponse>> permsByRole = allRPs.stream()
                .collect(Collectors.groupingBy(
                        rp -> rp.getRole().getId(),
                        Collectors.mapping(
                                rp -> rbacResponseMapper.toPermissionResponse(rp.getPermission()),
                                Collectors.toList())
                ));

        return roles.stream()
                .map(role -> rbacResponseMapper.toRoleResponse(
                        role, permsByRole.getOrDefault(role.getId(), List.of())))
                .collect(Collectors.toList());
    }

    @Override
    public RoleResponse getRoleById(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Role not found with id: %s", roleId)
                ));
        List<PermissionResponse> permissions = rbacResponseMapper.toPermissionResponseList(
                rolePermissionRepository.findPermissionsByRoleId(role.getId()));
        return rbacResponseMapper.toRoleResponse(role, permissions);
    }

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
                .build();
    }

    private RoleResponse convertToCompleteResponse(Role role) {
        List<PermissionResponse> permissions = rbacResponseMapper.toPermissionResponseList(
                rolePermissionRepository.findPermissionsByRoleId(role.getId()));
        return rbacResponseMapper.toRoleResponse(role, permissions);
    }
}