package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.dto.firm.response.FirmRolePermissionsResponse;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.dto.admin.response.RolePermissionResponse;
import com.lawfirm.erp.dto.admin.response.RoleResponse;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.RolePermission;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.security.CurrentUserResolver;
import com.lawfirm.erp.security.PermissionEvaluator;
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
public class FirmRoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PermissionEvaluator permissionEvaluator;
    private final CurrentUserResolver currentUserResolver;
    private final AuditService auditService;

    // ═══════════════════════════════════════════════════════════════════════
    // GET /api/v1/firm/roles
    // List all firm-scoped roles — read only, no system templates
    // ═══════════════════════════════════════════════════════════════════════
    public List<RoleResponse> getFirmRoles() {
        UUID firmId = getRequiredFirmId();

        return roleRepository.findByFirmIdAndIsSystemFalse(firmId)
                .stream()
                .map(this::toRoleResponse)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET /api/v1/firm/roles/{roleId}/permissions
    // What permissions does this role currently have?
    // Also shows ceiling — what the parent system role allows
    // ═══════════════════════════════════════════════════════════════════════
    public FirmRolePermissionsResponse getRolePermissions(UUID roleId) {
        UUID firmId = getRequiredFirmId();
        Role role = getValidatedFirmRole(roleId, firmId);

        // Current permissions on this firm-scoped role
        List<Permission> current = rolePermissionRepository.findPermissionsByRoleId(role.getId());

        // Ceiling — what the parent system role allows
        List<Permission> ceiling = List.of();
        if (role.getParentRoleId() != null) {
            ceiling = rolePermissionRepository.findPermissionsByRoleId(role.getParentRoleId());
        }

        Set<UUID> currentIds = current.stream().map(Permission::getId).collect(Collectors.toSet());

        return FirmRolePermissionsResponse.builder()
                .roleId(role.getId())
                .roleName(role.getRoleName())
                .roleCode(role.getRoleCode())
                // Permissions currently assigned to this role
                .currentPermissions(current.stream().map(this::toPermResponse).collect(Collectors.toList()))
                // All permissions available to assign (from parent ceiling)
                // Frontend uses this to build the checkbox list
                .availablePermissions(ceiling.stream()
                        .map(p -> FirmRolePermissionsResponse.AvailablePermission.builder()
                                .id(p.getId())
                                .code(p.getCode())
                                .action(p.getAction() != null ? p.getAction().name() : null)
                                .moduleCode(p.getModuleCode())
                                .description(p.getDescription())
                                .assigned(currentIds.contains(p.getId()))
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PUT /api/v1/firm/roles/{roleId}/permissions
    // Firm admin updates permissions on a firm-scoped role
    // Ceiling enforced — cannot exceed what the parent system role has
    // Invalidates JWT for all users holding this role immediately
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public RolePermissionResponse updateRolePermissions(UUID roleId, RolePermissionRequest request) {
        UUID firmId = getRequiredFirmId();
        UUID adminId = currentUserResolver.getCurrentUserId();

        Role role = getValidatedFirmRole(roleId, firmId);

        // ── Ceiling check ─────────────────────────────────────────────────
        // Firm admin cannot assign permissions beyond what the parent system
        // role has. parentRoleId was set when the role was cloned at firm creation.
        if (role.getParentRoleId() != null) {
            Set<UUID> ceilingIds = rolePermissionRepository
                    .findPermissionsByRoleId(role.getParentRoleId())
                    .stream()
                    .map(Permission::getId)
                    .collect(Collectors.toSet());

            for (UUID permId : request.getPermissionIds()) {
                if (!ceilingIds.contains(permId)) {
                    Permission p = permissionRepository.findById(permId)
                            .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permId));
                    throw new ForbiddenException(
                            "Permission '" + p.getCode() + "' is not allowed for role type '"
                                    + role.getRoleCode() + "'. Exceeds system ceiling."
                    );
                }
            }
        }

        // ── Validate all permission IDs exist ─────────────────────────────
        List<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds());
        if (permissions.size() != request.getPermissionIds().size()) {
            throw new ResourceNotFoundException("One or more permission IDs are invalid");
        }

        // ── Replace permissions ───────────────────────────────────────────
        rolePermissionRepository.deleteByRole(role);

        List<RolePermission> newRolePermissions = permissions.stream()
                .map(p -> {
                    RolePermission rp = RolePermission.builder()
                            .role(role)
                            .permission(p)
                            .build();
                    rp.setCreatedBy(adminId);
                    rp.setCreatedAt(LocalDateTime.now());
                    return rp;
                })
                .collect(Collectors.toList());

        rolePermissionRepository.saveAll(newRolePermissions);

        // ── Invalidate all users holding this role ────────────────────────
        // Their JWT carries old permissions — force re-login
        List<UUID> affectedUsers = userRepository.findUserIdsByRoleId(role.getId());
        for (UUID userId : affectedUsers) {
            userRepository.incrementPermissionVersion(userId);
            permissionEvaluator.clearUserCache(userId);
        }

        auditService.log(
                AuditAction.ROLE_PERMISSION_CHANGED,
                AuditEntity.ROLE,
                role.getId(),
                "Firm admin updated permissions for role: " + role.getRoleCode()
                        + " (" + permissions.size() + " permissions). "
                        + affectedUsers.size() + " user sessions invalidated."
        );

        log.info("Updated {} permissions for firm role '{}'. {} users invalidated.",
                permissions.size(), role.getRoleCode(), affectedUsers.size());

        // Return updated state
        return RolePermissionResponse.builder()
                .roleId(role.getId())
                .roleName(role.getRoleName())
                .roleCode(role.getRoleCode())
                .permissions(permissions.stream().map(this::toPermResponse).collect(Collectors.toList()))
                .build();
    }

    // ─── Guards ───────────────────────────────────────────────────────────

    private UUID getRequiredFirmId() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }

    /**
     * Validates:
     * 1. Role exists
     * 2. Role belongs to this firm (not another firm, not a system role)
     * 3. Role is not a system template (isSystem = false)
     */
    private Role getValidatedFirmRole(UUID roleId, UUID firmId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new ForbiddenException("Cannot modify system roles");
        }

        if (role.getFirm() == null || !role.getFirm().getId().equals(firmId)) {
            throw new ForbiddenException("Role does not belong to your firm");
        }

        return role;
    }

    // ─── Mappers ──────────────────────────────────────────────────────────

    private RoleResponse toRoleResponse(Role role) {
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

    private PermissionResponse toPermResponse(Permission p) {
        return PermissionResponse.builder()
                .id(p.getId())
                .action(p.getAction())
                .scope(p.getScope())
                .code(p.getCode())
                .description(p.getDescription())
                .isActive(p.isActive())
                .createdAt(p.getCreatedAt())
                .build();
    }
}