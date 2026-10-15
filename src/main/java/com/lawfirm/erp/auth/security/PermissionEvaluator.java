package com.lawfirm.erp.auth.security;

import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.repository.FirmModuleRepository;
import com.lawfirm.erp.firm.service.ModuleAccessResolver;
import com.lawfirm.erp.rbac.entity.Module;
import com.lawfirm.erp.rbac.repository.ModuleRepository;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionEvaluator {

    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final FirmModuleRepository firmModuleRepository;
    private final ModuleRepository moduleRepository;
    private final CurrentUserResolver currentUserResolver;

    public void require(String permissionCode) {
        AuthenticatedUser currentUser = getCurrentUser();

        if (currentUser == null) {
            throw new ForbiddenException("No authenticated user");
        }

        // Super Admins are platform-level (never firm-scoped) and intentionally bypass firm
        // permission checks. The firm-id guard makes the bypass unusable on a firm-bound token.
        if (currentUser.isSuperAdmin() && currentUser.getFirmId() == null) {
            return;
        }

        Set<String> permissions = loadPermissions(currentUser);

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

    private Set<String> loadPermissions(AuthenticatedUser authUser) {
        // Permissions travel on the JWT, so the normal path never touches the DB. The DB lookup is
        // only a fallback for tokens minted before the claim existed.
        if (authUser.getPermissions() != null && !authUser.getPermissions().isEmpty()) {
            return new HashSet<>(authUser.getPermissions());
        }

        Set<String> permissions = new HashSet<>();
        User user = userRepository.findById(authUser.getId()).orElse(null);
        if (user == null || user.getRole() == null) {
            log.warn("User {} has no role assigned - zero permissions", authUser.getId());
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
        if (firmId == null || moduleCode == null) {
            return false;
        }
        Module module = moduleRepository.findByCode(moduleCode).orElse(null);
        if (module == null) {
            return false;
        }
        return ModuleAccessResolver.isEnabled(module,
                ModuleAccessResolver.indexByModuleId(firmModuleRepository.findByFirmId(firmId)));
    }

    public void requireModuleAccess(String moduleCode) {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId != null && !hasModuleAccess(firmId, moduleCode)) {
            throw new ForbiddenException(
                    "Module '" + moduleCode + "' is not enabled for your firm. Please upgrade your plan."
            );
        }
    }

    // Kept as no-ops: there is no server-side permission cache to invalidate any more (permissions
    // come from the JWT and change on re-login). Callers are unchanged.
    public void clearUserCache(UUID userId) {
    }

    public void clearAllCache() {
    }
}