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

        // Group permissions by moduleCode
        Map<String, List<String>> permsByModule = new LinkedHashMap<>();
        for (Permission p : permissions) {
            if (p.getCode() == null || !p.getCode().contains(":")) continue;
            String[] parts = p.getCode().split(":", 2);
            permsByModule.computeIfAbsent(parts[0], k -> new ArrayList<>()).add(parts[1]);
        }

        //   FIX: Use isActive() not getActive()
        // For SUPER_ADMIN — all modules, always enabled
        if (user.isSuperAdmin()) {
            return moduleRepository.findAll().stream()
                    .filter(m -> m.isActive())  //   Use isActive()
                    .map(m -> MeResponse.ModuleAccess.builder()
                            .moduleCode(m.getCode())
                            .moduleName(m.getName())
                            .icon(m.getIcon())
                            .path(m.getPath())
                            .enabled(true)
                            .actions(permsByModule.getOrDefault(m.getCode(), List.of()))
                            .build())
                    .collect(Collectors.toList());
        }

        // For FIRM_USER and CLIENT — check which modules are enabled for the firm
        if (user.getFirm() == null) return List.of();

        //   FIX: Get enabled modules for this firm - use correct method
        List<FirmModule> firmModules = firmModuleRepository.findByFirmIdWithModule(user.getFirm().getId());

        //   FIX: Check isEnabled and expiresAt
        Set<String> enabledModuleCodes = firmModules.stream()
                .filter(fm -> Boolean.TRUE.equals(fm.getIsEnabled()))  
                .filter(fm -> fm.getExpiresAt() == null ||
                        fm.getExpiresAt().isAfter(LocalDateTime.now()))  
                .map(fm -> fm.getModule().getCode())  //   getModule()
                .collect(Collectors.toSet());

        // Build module access — only modules the user has at least one permission for
        return moduleRepository.findAll().stream()
                .filter(m -> m.isActive())  //   Use isActive()
                .filter(m -> permsByModule.containsKey(m.getCode()))
                .map(m -> MeResponse.ModuleAccess.builder()
                        .moduleCode(m.getCode())
                        .moduleName(m.getName())
                        .icon(m.getIcon())
                        .path(m.getPath())
                        .enabled(enabledModuleCodes.contains(m.getCode()))
                        .actions(permsByModule.getOrDefault(m.getCode(), List.of()))
                        .build())
                .collect(Collectors.toList());
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