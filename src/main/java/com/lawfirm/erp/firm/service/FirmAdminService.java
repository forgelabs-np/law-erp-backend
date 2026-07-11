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
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.security.CurrentUserResolver;
import com.lawfirm.erp.security.PermissionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirmAdminService {

    private final UserRepository userRepository;
    private final FirmRepository firmRepository;
    private final RoleRepository roleRepository;
    private final CurrentUserResolver currentUserResolver;
    private final PermissionEvaluator permissionEvaluator;
    private final AuditService auditService;

    /**
     * Get all firm admins across all firms (Super Admin only)
     */
    public List<FirmAdminResponse> getAllFirmAdmins() {
        // ✅ Use the NEW method that returns List
        List<Role> firmAdminRoles = roleRepository.findAllByRoleCode("FIRM_ADMIN");

        if (firmAdminRoles.isEmpty()) {
            log.warn("No FIRM_ADMIN roles found");
            return new ArrayList<>();
        }

        List<User> allFirmAdmins = new ArrayList<>();
        for (Role role : firmAdminRoles) {
            // Only include firm-scoped roles (firm_id != null)
            if (role.getFirm() != null) {
                List<User> usersWithRole = userRepository.findByRoleId(role.getId());
                allFirmAdmins.addAll(usersWithRole);
            }
        }

        return allFirmAdmins.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }


        /**
         * Get all firm admins for a specific firm
         */
        public List<FirmAdminResponse> getFirmAdminsByFirmId(UUID firmId) {
            Firm firm = firmRepository.findById(firmId)
                    .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));

            // ✅ Use existing method that returns Optional
            Role firmAdminRole = roleRepository
                    .findByFirmIdAndRoleCode(firmId, "FIRM_ADMIN")
                    .orElse(null);

            if (firmAdminRole == null) {
                log.warn("No FIRM_ADMIN role found for firm: {}", firmId);
                return new ArrayList<>();
            }

            List<User> firmAdmins = userRepository.findByFirmIdAndRoleId(firmId, firmAdminRole.getId());
            return firmAdmins.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        }


    /**
     * Get firm admin by ID
     */
    public FirmAdminResponse getFirmAdminById(UUID adminId) {
        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Verify user is FIRM_ADMIN
        if (user.getUserType() != UserType.FIRM_USER) {
            throw new BusinessRuleException("User is not a firm admin");
        }

        Role firmAdminRole = roleRepository.findByRoleCode("FIRM_ADMIN")
                .orElseThrow(() -> new ResourceNotFoundException("FIRM_ADMIN role not found"));

        // Check if user has FIRM_ADMIN role
        boolean isFirmAdmin = user.getRole() != null && user.getRole().getId().equals(firmAdminRole.getId());
        if (!isFirmAdmin) {
            throw new BusinessRuleException("User does not have FIRM_ADMIN role");
        }

        return toResponse(user);
    }

    /**
     * Toggle firm admin status (activate/deactivate)
     */
    @Transactional
    public FirmAdminResponse toggleFirmAdminStatus(UUID adminId) {
        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Verify user is FIRM_ADMIN
        Role firmAdminRole = roleRepository.findByRoleCode("FIRM_ADMIN")
                .orElseThrow(() -> new ResourceNotFoundException("FIRM_ADMIN role not found"));

        boolean isFirmAdmin = user.getRole() != null && user.getRole().getId().equals(firmAdminRole.getId());
        if (!isFirmAdmin) {
            throw new BusinessRuleException("User does not have FIRM_ADMIN role");
        }

        boolean newStatus = !user.isActive();
        user.setActive(newStatus);
        user = userRepository.save(user);

        // Invalidate cache
        permissionEvaluator.clearUserCache(user.getId());
        userRepository.incrementPermissionVersion(user.getId());

        // Audit log
        auditService.log(
                newStatus ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED,
                AuditEntity.USER,
                user.getId(),
                (newStatus ? "Activated" : "Deactivated") + " firm admin: " + user.getUsername()
        );

        log.info("Firm admin {} toggled to {}", user.getUsername(), newStatus ? "active" : "inactive");

        return toResponse(user);
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