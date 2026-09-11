package com.lawfirm.erp.modules.me.service;

import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.exception.UnauthorizedException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.entity.FirmModule;
import com.lawfirm.erp.firm.repository.FirmModuleRepository;
import com.lawfirm.erp.modules.me.dto.MeResponse;
import com.lawfirm.erp.rbac.entity.Module;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.ModuleRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeService {

    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final FirmModuleRepository firmModuleRepository;
    private final ModuleRepository moduleRepository;
    private final CurrentUserResolver currentUserResolver;
    private final SystemConfigService systemConfigService;

    public MeResponse getMe() {
        UUID userId = currentUserResolver.getCurrentUserId();
        if (userId == null) throw new UnauthorizedException("Not authenticated");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Load permissions from role
        List<Permission> permissions = user.getRole() != null
                ? rolePermissionRepository.findPermissionsByRoleId(user.getRole().getId())
                : List.of();

        List<String> permCodes = permissions.stream()
                .map(Permission::getCode)
                .collect(Collectors.toList());

        // Build module access list
        List<MeResponse.ModuleAccess> moduleAccess = buildModuleAccess(user, permissions);

        return MeResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mobileNo(user.getMobileNo())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .userType(user.getUserType() != null ? user.getUserType().name() : null)
                .firm(buildFirmInfo(user.getFirm()))
                .role(buildRoleInfo(user.getRole()))
                .permissions(permCodes)
                .modules(moduleAccess)
                .brandColorPrimary(resolveBrandPrimary(user))
                .brandColorSecondary(resolveBrandSecondary(user))
                .appName(resolveAppName())
                .isActive(user.isActive())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    private List<MeResponse.ModuleAccess> buildModuleAccess(User user, List<Permission> permissions) {

        // Group permissions by moduleCode (first segment of MODULE:ACTION)
        Map<String, List<String>> permsByModule = new LinkedHashMap<>();
        for (Permission p : permissions) {
            if (p.getCode() == null || !p.getCode().contains(":")) continue;
            String[] parts = p.getCode().split(":", 2);
            permsByModule.computeIfAbsent(parts[0], k -> new ArrayList<>()).add(parts[1]);
        }

        boolean superAdmin = user.isSuperAdmin();

        // FIRM_USER / CLIENT without a firm context have nothing to show
        if (!superAdmin && user.getFirm() == null) return List.of();

        // All modules (parent join-fetched to build the tree without N+1)
        List<Module> allModules = moduleRepository.findAllWithParentOrderByDisplayOrder();

        // Which module codes may this user see?
        //   SUPER_ADMIN -> every active module
        //   FIRM_USER/CLIENT -> active modules the user has at least one permission for
        Set<String> accessibleCodes = new LinkedHashSet<>();
        for (Module m : allModules) {
            if (!m.isActive()) continue;
            if (superAdmin || permsByModule.containsKey(m.getCode())) {
                accessibleCodes.add(m.getCode());
            }
        }

        // Promote parents of accessible sub-modules so sub-modules can nest
        // under their module even when the parent has no direct permission.
        boolean promoted;
        do {
            promoted = false;
            for (Module m : allModules) {
                if (!accessibleCodes.contains(m.getCode())) continue;
                Module parent = m.getParent();
                if (parent != null && parent.isActive() && !accessibleCodes.contains(parent.getCode())) {
                    accessibleCodes.add(parent.getCode());
                    promoted = true;
                }
            }
        } while (promoted);

        // Enabled module codes for this firm (SUPER_ADMIN: everything enabled)
        Set<String> enabledModuleCodes = new HashSet<>();
        if (!superAdmin && user.getFirm() != null) {
            enabledModuleCodes = firmModuleRepository.findByFirmIdWithModule(user.getFirm().getId()).stream()
                    .filter(fm -> Boolean.TRUE.equals(fm.getIsEnabled()))
                    .filter(fm -> fm.getExpiresAt() == null ||
                            fm.getExpiresAt().isAfter(LocalDateTime.now()))
                    .map(fm -> fm.getModule().getCode())
                    .collect(Collectors.toSet());
        }

        // Build a ModuleAccess for every accessible module (code -> access)
        Map<String, MeResponse.ModuleAccess> accessByCode = new LinkedHashMap<>();
        for (Module m : allModules) {
            if (!accessibleCodes.contains(m.getCode())) continue;
            accessByCode.put(m.getCode(), MeResponse.ModuleAccess.builder()
                    .moduleCode(m.getCode())
                    .moduleName(m.getName())
                    .icon(m.getIcon())
                    .path(m.getPath())
                    .enabled(superAdmin || enabledModuleCodes.contains(m.getCode()))
                    .actions(permsByModule.getOrDefault(m.getCode(), List.of()))
                    .subModules(new ArrayList<>())
                    .build());
        }

        // Nest sub-modules under their parent module (Module > SubModule)
        List<MeResponse.ModuleAccess> roots = new ArrayList<>();
        for (Module m : allModules) {
            MeResponse.ModuleAccess access = accessByCode.get(m.getCode());
            if (access == null) continue;

            Module parent = m.getParent();
            MeResponse.ModuleAccess parentAccess =
                    parent != null ? accessByCode.get(parent.getCode()) : null;

            if (parentAccess != null) {
                // Sub-modules inherit the parent's enabled state (firm modules
                // are tracked at the top-level module, not per sub-module)
                if (parentAccess.isEnabled()) {
                    access.setEnabled(true);
                }
                parentAccess.getSubModules().add(access);
            } else {
                roots.add(access);
            }
        }

        return roots;
    }

    private String resolveBrandPrimary(User user) {
        if (user.isSuperAdmin() || user.getFirm() == null) {
            return "#1A237E"; // default
        }
        return systemConfigService.getFirm(user.getFirm().getId(),
                SystemConfigService.KEY_BRAND_COLOR_PRIMARY).orElse("#1A237E");
    }

    private String resolveBrandSecondary(User user) {
        if (user.isSuperAdmin() || user.getFirm() == null) {
            return "#E3F2FD"; // default
        }
        return systemConfigService.getFirm(user.getFirm().getId(),
                SystemConfigService.KEY_BRAND_COLOR_SECONDARY).orElse("#E3F2FD");
    }

    private String resolveAppName() {
        return systemConfigService.getGlobal(SystemConfigService.KEY_APP_NAME)
                .orElse("NepalCRM");
    }

    private MeResponse.FirmInfo buildFirmInfo(Firm firm) {
        if (firm == null) return null;
        return MeResponse.FirmInfo.builder()
                .id(firm.getId())
                .name(firm.getName())
                .lawFirmCode(firm.getLawFirmCode())
                .email(firm.getEmail())
                .phone(firm.getPhone())
                .address(firm.getAddress())
                .jurisdiction(firm.getJurisdiction())
                .logoUrl(firm.getLogoUrl())
                .build();
    }

    private MeResponse.RoleInfo buildRoleInfo(Role role) {
        if (role == null) return null;
        return MeResponse.RoleInfo.builder()
                .id(role.getId())
                .name(role.getRoleName())
                .code(role.getRoleCode())
                .isSystem(Boolean.TRUE.equals(role.getIsSystem()))
                .build();
    }
}