package com.lawfirm.erp.auth.security;

import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.repository.FirmModuleRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FIX: previously read permissions via UserRoleRepository (the user_roles
 * join table), which is only ever written once - during firm-admin creation
 * in FirmService. Every advocate, paralegal, and client created afterward
 * had zero rows there, so getUserPermissions() silently returned an empty
 * set for them.
 *
 * The actual source of truth for "what role does this user have" is the
 * direct FK: User.role (used everywhere else - JwtUtil, getAuthorities()).
 * This version reads from that instead. user_roles / UserRole stays in the
 * schema for future multi-role support but is not used for permission
 * checks yet.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionEvaluator {

    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final FirmModuleRepository firmModuleRepository;
    private final CurrentUserResolver currentUserResolver;
    private final ConcurrentHashMap<UUID, Set<String>> permissionCache = new ConcurrentHashMap<>();

    public void require(String permissionCode) {
        AuthenticatedUser currentUser = getCurrentUser();

        if (currentUser == null) {
            throw new ForbiddenException("No authenticated user");
        }

        if (currentUser.isSuperAdmin()) {
            return;
        }

        Set<String> permissions = getUserPermissions(currentUser);

        if (!permissions.contains(permissionCode)) {
            log.warn("User {} missing permission: {}", currentUser.getUsername(), permissionCode);
            throw new ForbiddenException("Missing required permission: " + permissionCode);
        }
    }

    public boolean has(String permissionCode) {
        try {
            require(permissionCode);
            return true;
        } catch (ForbiddenException e) {
            return false;
        }
    }

    private Set<String> getUserPermissions(AuthenticatedUser authUser) {
        return permissionCache.computeIfAbsent(authUser.getId(), id -> {
            // Fast path: use the permission list already populated from JWT in the filter.
            // Avoids 2 extra DB queries (User + RolePermission) on every request.
            if (authUser.getPermissions() != null && !authUser.getPermissions().isEmpty()) {
                return new HashSet<>(authUser.getPermissions());
            }

            // Fallback: load from DB (for edge cases where JWT didn't carry permissions)
            Set<String> permissions = new HashSet<>();
            User user = userRepository.findById(id).orElse(null);
            if (user == null || user.getRole() == null) {
                log.warn("User {} has no role assigned - zero permissions", id);
                return permissions;
            }

            var rolePermissions = rolePermissionRepository.findByRole(user.getRole());
            for (var rp : rolePermissions) {
                if (Boolean.TRUE.equals(rp.getPermission().isActive())) {
                    permissions.add(rp.getPermission().getCode());
                }
            }

            log.debug("Loaded {} permissions for user: {} (role: {})",
                    permissions.size(), authUser.getUsername(), user.getRole().getRoleCode());
            return permissions;
        });
    }

    private AuthenticatedUser getCurrentUser() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return (AuthenticatedUser) request.getAttribute("authenticatedUser");
            }
        } catch (Exception e) {
            log.error("Failed to get current user from request", e);
        }
        return null;
    }

    public boolean hasModuleAccess(UUID firmId, String moduleCode) {
        return firmModuleRepository.existsByFirmIdAndModuleCodeAndIsEnabledTrue(firmId, moduleCode);
    }

    public void requireModuleAccess(String moduleCode) {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId != null && !hasModuleAccess(firmId, moduleCode)) {
            throw new ForbiddenException(
                    "Module '" + moduleCode + "' is not enabled for your firm. Please upgrade your plan."
            );
        }
    }

    /** Call this whenever a user's role or that role's permissions change. */
    public void clearUserCache(UUID userId) {
        permissionCache.remove(userId);
    }

    public void clearAllCache() {
        permissionCache.clear();
    }
}