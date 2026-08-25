package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.dto.firm.response.FirmRolePermissionsResponse;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.PermissionScope;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.dto.admin.response.RolePermissionResponse;
import com.lawfirm.erp.dto.admin.response.RoleResponse;
import com.lawfirm.erp.dto.firm.response.RoleUserResponse;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.entity.User;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirmRoleServiceImpl implements FirmRoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PermissionEvaluator permissionEvaluator;
    private final CurrentUserResolver currentUserResolver;
    private final AuditService auditService;

    // ═══════════════════════════════════════════════════════════════════════
    // GET /api/v1/firm/roles
    // List all firm-scoped roles + system roles applicable to FIRM_USER
    // ═══════════════════════════════════════════════════════════════════════
    public List<RoleResponse> getFirmRoles() {
        UUID firmId = getRequiredFirmId();

        List<Role> firmRoles = roleRepository.findByFirmIdAndIsSystemFalse(firmId);

        if (!firmRoles.isEmpty()) {
            // Firm has cloned roles — batch-load user counts and names in 2 queries
            List<UUID> roleIds = firmRoles.stream().map(Role::getId).collect(Collectors.toList());

            // Count users per role: returns [roleId, count] pairs
            Map<UUID, Integer> userCountMap = new HashMap<>();
            for (Object[] row : userRepository.countUsersByRoleIds(firmId)) {
                userCountMap.put((UUID) row[0], ((Number) row[1]).intValue());
            }

            // Map roleId -> list of user full names
            Map<UUID, List<String>> userNamesMap = new HashMap<>();
            for (Object[] row : userRepository.findUserNamesByRoleIds(firmId, roleIds)) {
                UUID roleId = (UUID) row[0];
                String name = (String) row[1];
                userNamesMap.computeIfAbsent(roleId, k -> new ArrayList<>()).add(name);
            }

            return firmRoles.stream()
                    .map(r -> toRoleResponse(r, userCountMap, userNamesMap))
                    .collect(Collectors.toList());
        }

        // Fallback for firms created before the role-cloning feature:
        // show system roles that are applicable to FIRM_USER
        // System fallback roles have no firm association — userCount is always 0
        return roleRepository.findByFirmIsNullAndIsSystemTrue()
                .stream()
                .filter(r -> r.getApplicableTo() == UserType.FIRM_USER)
                .map(r -> RoleResponse.builder()
                        .id(r.getId())
                        .name(r.getRoleName())
                        .code(r.getRoleCode())
                        .description(r.getDescription())
                        .isSystem(r.getIsSystem())
                        .isActive(r.isActive())
                        .userCount(0)
                        .assignedUserNames(List.of())
                        .createdAt(r.getCreatedAt())
                        .updatedAt(r.getUpdatedAt())
                        .build())
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

        // ── Batch-load permissions first (single query, avoids N+1) ──────
        List<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds());
        if (permissions.size() != request.getPermissionIds().size()) {
            throw new ResourceNotFoundException("One or more permission IDs are invalid");
        }

        // ── Ceiling check ─────────────────────────────────────────────────
        // A firm role may only hold permissions that its parent system role has.
        // (This is the same set the UI presents as "available permissions".)
        // GLOBAL-scope permissions are reserved for SUPER_ADMIN.
        if (role.getParentRoleId() != null) {
            Role parentRole = roleRepository.findById(role.getParentRoleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent system role not found"));

            Set<UUID> parentPermIds = rolePermissionRepository.findPermissionsByRoleId(parentRole.getId())
                    .stream().map(Permission::getId).collect(Collectors.toSet());

            for (Permission p : permissions) {
                if (!parentPermIds.contains(p.getId()) || p.getScope() == PermissionScope.GLOBAL) {
                    throw new ForbiddenException(
                            "Permission '" + p.getCode() + "' (scope: " + p.getScope()
                            + ") is not allowed for role type '" + parentRole.getRoleCode()
                            + "'. Exceeds system ceiling."
                    );
                }
            }
        }

        // ── Replace permissions ───────────────────────────────────────────
        rolePermissionRepository.deleteByRoleId(role.getId());

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

    // ═══════════════════════════════════════════════════════════════════════
    // GET /api/v1/firm/roles/{roleId}/users
    // List all users assigned to this role within the firm
    // ═══════════════════════════════════════════════════════════════════════
    public List<RoleUserResponse> getRoleUsers(UUID roleId) {
        UUID firmId = getRequiredFirmId();
        Role role = getValidatedFirmRole(roleId, firmId);

        return userRepository.findByFirmIdAndRoleId(firmId, role.getId())
                .stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    private RoleUserResponse toUserResponse(User user) {
        return RoleUserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .email(user.getEmail())
                .mobileNo(user.getMobileNo())
                .isActive(user.isActive())
                .build();
    }

    private RoleResponse toRoleResponse(Role role, Map<UUID, Integer> userCountMap,
                                         Map<UUID, List<String>> userNamesMap) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getRoleName())
                .code(role.getRoleCode())
                .description(role.getDescription())
                .isSystem(role.getIsSystem())
                .isActive(role.isActive())
                .userCount(userCountMap.getOrDefault(role.getId(), 0))
                .assignedUserNames(userNamesMap.getOrDefault(role.getId(), List.of()))
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