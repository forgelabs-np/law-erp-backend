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
public class FirmAdminService {

    private final UserRepository userRepository;
    private final FirmRepository firmRepository;
    private final CurrentUserResolver currentUserResolver;
    private final PermissionEvaluator permissionEvaluator;
    private final AuditService auditService;

    // RoleRepository removed entirely — we never look up roles here.
    // We query users directly using role.roleCode in JPQL.
    // This avoids the NonUniqueResultException caused by system + firm-scoped
    // roles having the same roleCode.

    /**
     * All firm admins across all firms — Super Admin only.
     * Uses UserRepository.findAllFirmAdmins() which queries users directly.
     */
    public List<FirmAdminResponse> getAllFirmAdmins() {
        return userRepository.findAllFirmAdmins()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * All firm admins for a specific firm — Super Admin only.
     */
    public List<FirmAdminResponse> getFirmAdminsByFirmId(UUID firmId) {
        firmRepository.findById(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));

        return userRepository.findFirmAdminsByFirmId(firmId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get one firm admin by their user ID.
     */
    public FirmAdminResponse getFirmAdminById(UUID adminId) {
        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        validateIsFirmAdmin(user);

        return toResponse(user);
    }

    /**
     * Toggle firm admin active/inactive — Super Admin only.
     * Invalidates the user's JWT immediately.
     */
    @Transactional
    public FirmAdminResponse toggleFirmAdminStatus(UUID adminId) {
        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        validateIsFirmAdmin(user);

        boolean newStatus = !user.isActive();
        user.setActive(newStatus);
        userRepository.save(user);

        // Invalidate JWT — must re-login after status change
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

    /**
     * Validates user is a FIRM_USER with FIRM_ADMIN roleCode.
     * Does NOT call roleRepository — checks the role already loaded on the user.
     */
    private void validateIsFirmAdmin(User user) {
        if (user.getUserType() != UserType.FIRM_USER) {
            throw new BusinessRuleException("User is not a firm user");
        }
        if (user.getRole() == null || !"FIRM_ADMIN".equals(user.getRole().getRoleCode())) {
            throw new BusinessRuleException("User does not have FIRM_ADMIN role");
        }
    }

    private FirmAdminResponse toResponse(User user) {
        return FirmAdminResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .mobileNo(user.getMobileNo())
                .fullName(user.getFullName())
                .firmId(user.getFirmId())
                .firmName(user.getFirm() != null ? user.getFirm().getName() : null)
                .firmCode(user.getFirm() != null ? user.getFirm().getLawFirmCode() : null)
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}