package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.firm.response.FirmAdminResponse;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.PermissionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirmAdminServiceImpl implements FirmAdminService {

    private final UserRepository userRepository;
    private final FirmRepository firmRepository;
    private final CurrentUserResolver currentUserResolver;
    private final PermissionEvaluator permissionEvaluator;
    private final AuditService auditService;


    public List<FirmAdminResponse> getAllFirmAdmins() {
        return userRepository.findAllFirmAdmins()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<FirmAdminResponse> getFirmAdminsByFirmId(UUID firmId) {
        firmRepository.findById(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));

        return userRepository.findFirmAdminsByFirmId(firmId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public FirmAdminResponse getFirmAdminById(UUID adminId) {
        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        validateIsFirmAdmin(user);

        return toResponse(user);
    }

    @Transactional
    public FirmAdminResponse toggleFirmAdminStatus(UUID adminId) {
        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        validateIsFirmAdmin(user);

        boolean newStatus = !user.isActive();
        user.setActive(newStatus);
        userRepository.save(user);

        userRepository.incrementPermissionVersion(adminId);
        permissionEvaluator.clearUserCache(adminId);

        auditService.log(
                newStatus ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED,
                AuditEntity.USER,
                adminId,
                (newStatus ? "Activated" : "Deactivated") + " firm admin: " + user.getUsername()
        );

        log.info("Firm admin {} toggled to {}", user.getUsername(), newStatus ? "active" : "inactive");

        return toResponse(user);
    }

    private void validateIsFirmAdmin(User user) {
        if (user.getUserType() != UserType.FIRM) {
            throw new BusinessRuleException("User is not a firm admin");
        }
        if (user.getRole() == null || !"FIRM_ADMIN".equals(user.getRole().getRoleCode())) {
            throw new BusinessRuleException("User does not have FIRM_ADMIN role");
        }
    }

    private FirmAdminResponse toResponse(User user) {
        Firm firm = user.getFirm();
        return FirmAdminResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .mobileNo(user.getMobileNo())
                .fullName(user.getFullName())
                .roleId(user.getRole() != null ? user.getRole().getId() : null)
                .roleName(user.getRole() != null ? user.getRole().getRoleCode() : null)
                .firmId(user.getFirmId())
                .firmName(firm != null ? firm.getName() : null)
                .firmCode(firm != null ? firm.getLawFirmCode() : null)
                .firmEmail(firm != null ? firm.getEmail() : null)
                .firmStatus(firm != null ? firm.getStatus() : null)
                .firmType(firm != null && firm.getFirmType() != null ? firm.getFirmType().name() : null)
                .firmAddress(firm != null ? firm.getAddress() : null)
                .firmPhone(firm != null ? firm.getPhone() : null)
                .isTrial(firm != null && Boolean.TRUE.equals(firm.getIsTrial()))
                .trialDays(firm != null ? firm.getTrialDays() : null)
                .trialExpiresAt(firm != null ? firm.getTrialExpiresAt() : null)
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}