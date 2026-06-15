package com.lawfirm.erp.security;

import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.firm.repository.FirmModuleRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.UserRoleRepository;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionEvaluator {

    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final ConcurrentHashMap<UUID, Set<String>> permissionCache = new ConcurrentHashMap<>();
    private final FirmModuleRepository firmModuleRepository;
    private final CurrentUserResolver currentUserResolver;

    public void require(String permissionCode) {
        AuthenticatedUser currentUser = getCurrentUser();

        if (currentUser == null) {
            throw new ForbiddenException("No authenticated user");
        }

        // Super Admin has all permissions
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

    private Set<String> getUserPermissions(AuthenticatedUser user) {
        return permissionCache.computeIfAbsent(user.getId(), id -> {
            Set<String> permissions = new HashSet<>();

            // Get user's roles
            var userRoles = userRoleRepository.findByUserId(id);

            for (var ur : userRoles) {
                var rolePermissions = rolePermissionRepository.findByRole(ur.getRole());
                for (var rp : rolePermissions) {
                    permissions.add(rp.getPermission().getCode());
                }
            }

            log.debug("Loaded {} permissions for user: {}", permissions.size(), user.getUsername());
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

    public void clearUserCache(UUID userId) {
        permissionCache.remove(userId);
    }

    public void clearAllCache() {
        permissionCache.clear();
    }
}