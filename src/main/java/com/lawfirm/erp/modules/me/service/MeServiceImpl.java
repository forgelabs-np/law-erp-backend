package com.lawfirm.erp.modules.me.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.exception.UnauthorizedException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.FirmModule;
import com.lawfirm.erp.firm.repository.FirmModuleRepository;
import com.lawfirm.erp.modules.me.dto.MeResponse;
import com.lawfirm.erp.modules.me.mapper.MeMapper;
import com.lawfirm.erp.rbac.entity.Module;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.repository.ModuleRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeServiceImpl implements MeService {

    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final FirmModuleRepository firmModuleRepository;
    private final ModuleRepository moduleRepository;
    private final CurrentUserResolver currentUserResolver;
    private final SystemConfigService systemConfigService;
    private final MeMapper meMapper;

    @Override
    public MeResponse getMe() {
        UUID userId = currentUserResolver.getCurrentUserId();
        if (userId == null) throw new UnauthorizedException("Not authenticated");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Gate: suspended firm users cannot access anything
        if (user.getFirm() != null && user.getFirm().getStatus() == FirmStatus.SUSPENDED) {
            throw new ForbiddenException("Your firm account has been suspended. Contact support.");
        }

        // Gate: trial expired — firm users get empty modules
        boolean trialExpired = false;
        if (user.getFirm() != null && Boolean.TRUE.equals(user.getFirm().getIsTrial())
                && user.getFirm().getTrialExpiresAt() != null
                && user.getFirm().getTrialExpiresAt().isBefore(LocalDateTime.now())) {
            trialExpired = true;
        }

        List<Permission> permissions = user.getRole() != null
                ? rolePermissionRepository.findPermissionsByRoleId(user.getRole().getId())
                : List.of();

        List<String> permCodes = permissions.stream()
                .map(Permission::getCode)
                .collect(Collectors.toList());

        List<MeResponse.ModuleAccess> moduleAccess = trialExpired
                ? List.of()
                : buildModuleAccess(user, permissions);

        return MeResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mobileNo(user.getMobileNo())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .userType(user.getUserType() != null ? user.getUserType().name() : null)
                .firm(meMapper.toFirmInfo(user.getFirm()))
                .role(meMapper.toRoleInfo(user.getRole()))
                .permissions(trialExpired ? List.of() : permCodes)
                .modules(moduleAccess)
                .brandColorPrimary(resolveBrandPrimary(user))
                .brandColorSecondary(resolveBrandSecondary(user))
                .appName(resolveAppName())
                .isActive(user.isActive())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    private List<MeResponse.ModuleAccess> buildModuleAccess(User user, List<Permission> permissions) {
        Map<String, List<String>> permsByModule = new LinkedHashMap<>();
        for (Permission p : permissions) {
            if (p.getCode() == null || !p.getCode().contains(":")) continue;
            String[] parts = p.getCode().split(":", 2);
            permsByModule.computeIfAbsent(parts[0], k -> new ArrayList<>()).add(parts[1]);
        }

        boolean superAdmin = user.isSuperAdmin();
        if (!superAdmin && user.getFirm() == null) return List.of();

        List<Module> allModules = moduleRepository.findAllWithParentOrderByDisplayOrder();

        Set<String> accessibleCodes = new LinkedHashSet<>();
        for (Module m : allModules) {
            if (!m.isActive()) continue;
            if (superAdmin || permsByModule.containsKey(m.getCode())) {
                accessibleCodes.add(m.getCode());
            }
        }

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

        Set<String> enabledModuleCodes = new HashSet<>();
        if (!superAdmin && user.getFirm() != null) {
            enabledModuleCodes = firmModuleRepository.findByFirmIdWithModule(user.getFirm().getId()).stream()
                    .filter(fm -> Boolean.TRUE.equals(fm.getIsEnabled()))
                    .filter(fm -> fm.getExpiresAt() == null || fm.getExpiresAt().isAfter(LocalDateTime.now()))
                    .map(fm -> fm.getModule().getCode())
                    .collect(Collectors.toSet());
        }

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

        List<MeResponse.ModuleAccess> roots = new ArrayList<>();
        for (Module m : allModules) {
            MeResponse.ModuleAccess access = accessByCode.get(m.getCode());
            if (access == null) continue;

            Module parent = m.getParent();
            MeResponse.ModuleAccess parentAccess = parent != null ? accessByCode.get(parent.getCode()) : null;

            if (parentAccess != null) {
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
            return "#1A237E";
        }
        return systemConfigService.getFirm(user.getFirm().getId(),
                SystemConfigService.KEY_BRAND_COLOR_PRIMARY).orElse("#1A237E");
    }

    private String resolveBrandSecondary(User user) {
        if (user.isSuperAdmin() || user.getFirm() == null) {
            return "#E3F2FD";
        }
        return systemConfigService.getFirm(user.getFirm().getId(),
                SystemConfigService.KEY_BRAND_COLOR_SECONDARY).orElse("#E3F2FD");
    }

    private String resolveAppName() {
        return systemConfigService.getGlobal(SystemConfigService.KEY_APP_NAME)
                .orElse("NepalCRM");
    }
}
