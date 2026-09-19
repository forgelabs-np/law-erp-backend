package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.dto.firm.request.EnableModuleRequest;
import com.lawfirm.erp.dto.firm.response.FirmModuleResponse;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.entity.FirmModule;
import com.lawfirm.erp.firm.repository.FirmModuleRepository;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.rbac.entity.Module;
import com.lawfirm.erp.rbac.repository.ModuleRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirmModuleServiceImpl implements FirmModuleService {

    private final FirmModuleRepository firmModuleRepository;
    private final FirmRepository firmRepository;
    private final ModuleRepository moduleRepository;
    private final CurrentUserResolver currentUserResolver;
    private final AuditService auditService;

    /**
     * Enabling or disabling a module applies to its whole sub-tree, so a firm can never
     * end up with a sub-module of a disabled parent — and granting TESTCONFIG grants
     * TESTCONFIG 1 / TESTCONFIG 2 without a second call. A sub-module granted this way can
     * still be switched off on its own afterwards, because its own row wins.
     */
    @Transactional
    public FirmModuleResponse enableModuleForFirm(UUID firmId, EnableModuleRequest request) {
        Firm firm = firmRepository.findById(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));

        Module module = moduleRepository.findById(request.getModuleId())
                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));

        boolean enabled = Boolean.TRUE.equals(request.getIsEnabled());
        List<Module> subtree = new ArrayList<>();
        collectSubtree(module, subtree);

        FirmModule target = null;
        for (Module node : subtree) {
            FirmModule firmModule = firmModuleRepository
                    .findByFirmIdAndModuleId(firmId, node.getId())
                    .orElse(null);

            if (firmModule == null) {
                firmModule = FirmModule.builder()
                        .firm(firm)
                        .module(node)
                        .isEnabled(enabled)
                        .enabledAt(enabled ? LocalDateTime.now() : null)
                        .build();
            } else {
                firmModule.setIsEnabled(enabled);
                if (enabled) {
                    firmModule.setEnabledAt(LocalDateTime.now());
                }
            }

            FirmModule saved = firmModuleRepository.save(firmModule);
            if (node.getId().equals(module.getId())) {
                target = saved;
            }
        }

        int subModuleCount = subtree.size() - 1;
        log.info("Module {} {} for firm {} ({} sub-module(s) followed)",
                module.getCode(), enabled ? "enabled" : "disabled", firm.getLawFirmCode(), subModuleCount);

        auditService.log(
                enabled ? AuditAction.FIRM_MODULE_ENABLED : AuditAction.FIRM_MODULE_DISABLED,
                AuditEntity.FIRM_MODULE,
                target.getId(),
                (enabled ? "Enabled" : "Disabled") + " module: " + module.getCode() +
                        " for firm: " + firm.getLawFirmCode() +
                        (subModuleCount > 0 ? " (+ " + subModuleCount + " sub-modules)" : "")
        );

        return toResponse(target);
    }

    private void collectSubtree(Module module, List<Module> collected) {
        collected.add(module);
        if (module.getSubModules() != null) {
            for (Module child : module.getSubModules()) {
                collectSubtree(child, collected);
            }
        }
    }

    public FirmModuleResponse getModuleConfig(UUID firmId, UUID moduleId) {
        FirmModule firmModule = firmModuleRepository
                .findByFirmIdAndModuleId(firmId, moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module config not found"));
        return toResponse(firmModule);
    }

    public List<FirmModuleResponse> getFirmModules(UUID firmId) {
        return firmModuleRepository.findByFirmIdWithModule(firmId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<FirmModuleResponse> getMyEnabledModules() {
        UUID firmId = getCurrentFirmId();
        return firmModuleRepository.findEnabledModulesByFirmId(firmId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public boolean isModuleEnabled(UUID firmId, String moduleCode) {
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

    public Map<String, Boolean> resolveEnabledModules(UUID firmId) {
        if (firmId == null) {
            return Map.of();
        }
        Map<UUID, FirmModule> rowsByModuleId =
                ModuleAccessResolver.indexByModuleId(firmModuleRepository.findByFirmId(firmId));

        Map<String, Boolean> resolved = new LinkedHashMap<>();
        for (Module module : moduleRepository.findAllWithParentOrderByDisplayOrder()) {
            resolved.put(module.getCode(), ModuleAccessResolver.isEnabled(module, rowsByModuleId));
        }
        return resolved;
    }

    private UUID getCurrentFirmId() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) {
            throw new ForbiddenException("This action requires a firm context");
        }
        return firmId;
    }

    private FirmModuleResponse toResponse(FirmModule firmModule) {
        return FirmModuleResponse.builder()
                .id(firmModule.getId())
                .moduleId(firmModule.getModule().getId())
                .moduleName(firmModule.getModule().getName())
                .moduleCode(firmModule.getModule().getCode())
                .isEnabled(firmModule.getIsEnabled())
                .enabledAt(firmModule.getEnabledAt())
                .expiresAt(firmModule.getExpiresAt())
                .isTrial(firmModule.getIsTrial())
                .maxFileSizeMb(firmModule.getMaxFileSizeMb())
                .allowedExtensions(firmModule.getAllowedExtensions())
                .notes(firmModule.getNotes())
                .build();
    }
}
