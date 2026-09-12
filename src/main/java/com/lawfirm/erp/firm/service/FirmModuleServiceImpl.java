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
import java.util.List;
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

    @Transactional
    public FirmModuleResponse enableModuleForFirm(UUID firmId, EnableModuleRequest request) {
        Firm firm = firmRepository.findById(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));

        Module module = moduleRepository.findById(request.getModuleId())
                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));

        FirmModule firmModule = firmModuleRepository
                .findByFirmIdAndModuleId(firmId, module.getId())
                .orElse(null);

        if (firmModule != null) {
            firmModule.setIsEnabled(request.getIsEnabled());
            if (request.getIsEnabled()) {
                firmModule.setEnabledAt(LocalDateTime.now());
            }

            firmModule = firmModuleRepository.save(firmModule);

            auditService.log(
                    AuditAction.FIRM_MODULE_CONFIGURED,
                    AuditEntity.FIRM_MODULE,
                    firmModule.getId(),
                    "Module " + module.getCode() + " configured for firm " + firm.getLawFirmCode() +
                            " (enabled: " + request.getIsEnabled() + ")"
            );
        } else {
            firmModule = FirmModule.builder()
                    .firm(firm)
                    .module(module)
                    .isEnabled(request.getIsEnabled())
                    .enabledAt(request.getIsEnabled() ? LocalDateTime.now() : null)
                    .build();

            firmModule = firmModuleRepository.save(firmModule);
        }

        log.info("Module {} {} for firm {}", module.getCode(),
                request.getIsEnabled() ? "enabled" : "disabled", firm.getLawFirmCode());

        auditService.log(
                request.getIsEnabled() ? AuditAction.FIRM_MODULE_ENABLED : AuditAction.FIRM_MODULE_DISABLED,
                AuditEntity.FIRM_MODULE,
                firmModule.getId(),
                (request.getIsEnabled() ? "Enabled" : "Disabled") + " module: " + module.getCode() +
                        " for firm: " + firm.getLawFirmCode()
        );

        return toResponse(firmModule);
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
        return firmModuleRepository.existsByFirmIdAndModuleCodeAndIsEnabledTrue(firmId, moduleCode);
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