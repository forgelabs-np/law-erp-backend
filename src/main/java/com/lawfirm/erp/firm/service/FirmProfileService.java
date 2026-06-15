package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.firm.request.UpdateFirmProfileRequest;
import com.lawfirm.erp.dto.firm.response.FirmProfileResponse;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirmProfileService {

    private final FirmRepository firmRepository;
    private final UserRepository userRepository;
    private final CurrentUserResolver currentUserResolver;

    public FirmProfileResponse getMyFirmProfile() {
        UUID firmId = getCurrentFirmId();
        Firm firm = getFirmById(firmId);
        return toProfileResponse(firm);
    }

    @Transactional
    public FirmProfileResponse updateMyFirmProfile(UpdateFirmProfileRequest request) {
        UUID firmId = getCurrentFirmId();
        Firm firm = getFirmById(firmId);

        // Update only fields that are provided
        if (request.getName() != null) firm.setName(request.getName());
        if (request.getEmail() != null) firm.setEmail(request.getEmail());
        if (request.getPhone() != null) firm.setPhone(request.getPhone());
        if (request.getAddress() != null) firm.setAddress(request.getAddress());
        if (request.getJurisdiction() != null) firm.setJurisdiction(request.getJurisdiction());
        if (request.getLogoUrl() != null) firm.setLogoUrl(request.getLogoUrl());

        firm = firmRepository.save(firm);
        log.info("Firm profile updated: {}", firm.getLawFirmCode());

        return toProfileResponse(firm);
    }

    private UUID getCurrentFirmId() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) {
            throw new ForbiddenException("This action requires a firm context");
        }
        return firmId;
    }

    private Firm getFirmById(UUID firmId) {
        return firmRepository.findById(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));
    }

    private FirmProfileResponse toProfileResponse(Firm firm) {
        long employeeCount = userRepository.countByFirmIdAndUserType(firm.getId(), com.lawfirm.erp.common.enums.UserType.FIRM_USER);
        long customerCount = userRepository.countByFirmIdAndUserType(firm.getId(), com.lawfirm.erp.common.enums.UserType.CLIENT);

        return FirmProfileResponse.builder()
                .id(firm.getId())
                .lawFirmCode(firm.getLawFirmCode())
                .name(firm.getName())
                .firmType(firm.getFirmType())
                .status(firm.getStatus())
                .planTier(firm.getPlanTier())
                .email(firm.getEmail())
                .phone(firm.getPhone())
                .address(firm.getAddress())
                .jurisdiction(firm.getJurisdiction())
                .logoUrl(firm.getLogoUrl())
                .maxEmployees(firm.getMaxEmployees())
                .currentEmployeeCount((int) employeeCount)
                .currentCustomerCount((int) customerCount)
                .createdAt(firm.getCreatedAt())
                .build();
    }
}