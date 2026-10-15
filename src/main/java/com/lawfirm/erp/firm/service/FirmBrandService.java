package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.dto.firm.request.SetFirmThemeRequest;
import com.lawfirm.erp.dto.firm.response.FirmProfileResponse;
import com.lawfirm.erp.dto.firm.response.LogoResponse;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.firm.service.FirmStorageService;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirmBrandService {

    private final FirmRepository firmRepository;
    private final CurrentUserResolver currentUserResolver;
    private final AuditService auditService;
    private final FirmStorageService firmStorageService;
    public FirmProfileResponse getMyFirmProfile() {
        Firm firm = firmRepository.findById(currentUserResolver.getCurrentFirmId())
                .orElseThrow(() -> new ForbiddenException("This action requires a firm context"));
        return toProfileResponse(firm);
    }

    @Transactional
    public FirmProfileResponse setMyFirmTheme(SetFirmThemeRequest request) {
        Firm firm = requireMyFirm();
        String oldPrimary = firm.getBrandPrimaryHex();
        String oldSecondary = firm.getBrandSecondaryHex();

        if (request.getBrandPrimaryHex() != null) {
            firm.setBrandPrimaryHex(BrandColorValidator.normalizeHex(request.getBrandPrimaryHex()));
        }
        if (request.getBrandSecondaryHex() != null) {
            firm.setBrandSecondaryHex(BrandColorValidator.normalizeHex(request.getBrandSecondaryHex()));
        }
        firm = firmRepository.save(firm);

        log.info("Firm {} theme updated", firm.getLawFirmCode());

        auditService.log(AuditAction.FIRM_UPDATED, AuditEntity.FIRM, firm.getId(),
                "Firm theme updated: " + firm.getLawFirmCode()
                        + " (primary: " + orDash(oldPrimary) + " -> " + orDash(firm.getBrandPrimaryHex())
                        + ", secondary: " + orDash(oldSecondary) + " -> " + orDash(firm.getBrandSecondaryHex()) + ")");

        return toProfileResponse(firm);
    }

    @Transactional
    public LogoResponse toggleLogoAllowed(Boolean allowed) {
        Firm firm = requireMyFirm();
        if (allowed == null) {
            throw new BusinessRuleException("logoAllowed is required");
        }
        Boolean before = firm.getLogoAllowed();
        firm.setLogoAllowed(allowed);
        firm = firmRepository.save(firm);
        log.info("Firm {} logoAllowed updated: {} -> {}", firm.getLawFirmCode(), orDash(before), allowed);

        auditService.log(AuditAction.FIRM_UPDATED, AuditEntity.FIRM, firm.getId(),
                "Firm logo allowed toggled: " + firm.getLawFirmCode() + " (" + orDash(before) + " -> " + allowed + ")");

        return LogoResponse.builder()
                .logoUrl(firm.getLogoUrl())
                .logoAllowed(Boolean.TRUE.equals(firm.getLogoAllowed()))
                .build();
    }

    public LogoResponse getMyFirmLogo() {
        Firm firm = requireMyFirm();
        return LogoResponse.builder()
                .logoUrl(firm.getLogoUrl())
                .logoAllowed(Boolean.TRUE.equals(firm.getLogoAllowed()))
                .build();
    }

    @Transactional
    public LogoResponse uploadMyFirmLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("A logo file is required");
        }
        Firm firm = requireMyFirm();
        if (!Boolean.TRUE.equals(firm.getLogoAllowed())) {
            throw new ForbiddenException("Logo uploads are not allowed for this firm. "
                    + "Enable them first via the theme/logo settings.");
        }

        String previousUrl = firm.getLogoUrl();
        FirmStorageService.LogoUploadResult result = firmStorageService.storeFirmLogo(firm.getId(), file);
        if (previousUrl != null) {
            firmStorageService.deleteFirmLogo(firm.getId(), previousUrl);
        }
        firm.setLogoUrl(result.presignUrl());
        firm = firmRepository.save(firm);

        log.info("Firm {} logo uploaded -> {}", firm.getLawFirmCode(), result.storedKey());
        auditService.log(AuditAction.FIRM_UPDATED, AuditEntity.FIRM, firm.getId(),
                "Firm logo uploaded: " + firm.getLawFirmCode() + " (previous: " + orDash(previousUrl) + ")");

        return LogoResponse.builder()
                .logoUrl(firm.getLogoUrl())
                .logoAllowed(Boolean.TRUE.equals(firm.getLogoAllowed()))
                .build();
    }

    private Firm requireMyFirm() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) {
            throw new ForbiddenException("This action requires a firm context");
        }
        return firmRepository.findById(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));
    }

    private String orDash(Object value) {
        return value == null ? "-" : value.toString();
    }

    private FirmProfileResponse toProfileResponse(Firm firm) {
        return FirmProfileResponse.builder()
                .id(firm.getId())
                .lawFirmCode(firm.getLawFirmCode())
                .name(firm.getName())
                .firmType(firm.getFirmType())
                .status(firm.getStatus())
                .email(firm.getEmail())
                .phone(firm.getPhone())
                .address(firm.getAddress())
                .jurisdiction(firm.getJurisdiction())
                .logoUrl(firm.getLogoUrl())
                .logoAllowed(Boolean.TRUE.equals(firm.getLogoAllowed()))
                .brandPrimaryHex(normalizeHex(firm.getBrandPrimaryHex()))
                .brandSecondaryHex(normalizeHex(firm.getBrandSecondaryHex()))
                .isPersonalColor(BrandColorValidator.hasPersonalColors(
                        firm.getBrandPrimaryHex(), firm.getBrandSecondaryHex()))
                .isTrial(Boolean.TRUE.equals(firm.getIsTrial()))
                .trialDays(firm.getTrialDays())
                .trialExpiresAt(firm.getTrialExpiresAt())
                .createdAt(firm.getCreatedAt())
                .build();
    }

    private String normalizeHex(String hex) {
        if (hex == null) {
            return null;
        }
        return BrandColorValidator.normalizeHex(hex);
    }
}
