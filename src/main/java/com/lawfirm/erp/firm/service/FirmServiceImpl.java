package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.constant.RoleCode;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.email.service.EmailService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.DuplicateResourceException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.firm.request.CreateFirmRequest;
import com.lawfirm.erp.dto.firm.response.FirmCreationResponse;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.UserRole;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.rbac.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirmServiceImpl implements FirmService {

    private final FirmRepository firmRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final EmailService emailService;
    private final SystemConfigService systemConfigService;

    @Transactional
    public FirmCreationResponse createFirm(CreateFirmRequest request) {

        String firmCode = request.getLawFirmCode().toUpperCase().trim();
        if (firmRepository.existsByLawFirmCode(firmCode)) {
            throw new DuplicateResourceException("Firm code '" + firmCode + "' already exists");
        }
        if (userRepository.existsByUsernameAndFirmId(request.getAdminUsername(), null)) {
            throw new DuplicateResourceException("Admin username already exists");
        }
        if (userRepository.existsByEmailAndFirmId(request.getAdminEmail(), null)) {
            throw new DuplicateResourceException("Admin email already exists");
        }
        if (userRepository.existsByMobileNoAndFirmId(request.getAdminMobileNo(), null)) {
            throw new DuplicateResourceException("Admin mobile number already exists");
        }

        boolean isTrial = Boolean.TRUE.equals(request.getIsTrial());
        Firm firm = Firm.builder()
                .lawFirmCode(firmCode)
                .name(request.getName())
                .firmType(request.getFirmType())
                .status(isTrial ? FirmStatus.TRIAL : FirmStatus.ACTIVE)
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .jurisdiction(request.getJurisdiction())
                .isTrial(isTrial)
                .build();

        if (isTrial && request.getTrialDays() != null && request.getTrialDays() > 0) {
            firm.setTrialDays(request.getTrialDays());
            firm.setTrialStartedAt(LocalDateTime.now());
            firm.setTrialExpiresAt(LocalDateTime.now().plusDays(request.getTrialDays()));
        }
        firm = firmRepository.save(firm);
        log.info("Firm created: {}", firm.getLawFirmCode());

        // FIX: previously FIRM_ADMIN was skipped here, then looked up below -> 404
        cloneSystemRolesForFirm(firm);

        Role firmScopedAdminRole = roleRepository
                .findByFirmIdAndRoleCode(firm.getId(), "FIRM_ADMIN")
                .orElseThrow(() -> new BusinessRuleException(
                        "Firm-scoped " + RoleCode.FIRM_ADMIN + " role not found after cloning — check DataInitializer seeded " + RoleCode.FIRM_ADMIN + " system role"));

        // DB-driven MFA policy: force MFA on the new firm admin only while enforcement
        // is on and FIRM_ADMIN is in the required roles (default: on).
        boolean adminMfaRequired = systemConfigService.isMfaEnabled()
                && systemConfigService.mfaRequiredRoleCodes().contains(RoleCode.FIRM_ADMIN);

        User admin = User.builder()
                .username(request.getAdminUsername())
                .email(request.getAdminEmail())
                .mobileNo(request.getAdminMobileNo())
                .password(passwordEncoder.encode(request.getAdminPassword()))
                .fullName(request.getAdminFullName())
                .firm(firm)
                .role(firmScopedAdminRole)
                .userType(UserType.FIRM)
                .isEmailVerified(true)
                .isMobileVerified(true)
                .isBlocked(false)
                .loginAttempts(0)
                .mustChangePassword(true)
                .mfaEnabled(adminMfaRequired)
                .build();
        admin.setActive(true);
        admin = userRepository.save(admin);

        UserRole userRole = UserRole.builder()
                .user(admin)
                .role(firmScopedAdminRole)
                .build();
        userRoleRepository.save(userRole);

        log.info("Firm '{}' created with admin '{}'", firm.getLawFirmCode(), admin.getUsername());

        emailService.sendWelcomeFirmAdmin(
                firm.getId(),
                admin.getId(),
                admin.getEmail(),
                admin.getFullName(),
                admin.getUsername(),
                request.getAdminPassword(),
                firm.getName(),
                firm.getLawFirmCode()
        );

        auditService.logExplicit(
                null,
                admin.getId(),
                "S",
                AuditAction.FIRM_CREATED,
                AuditEntity.FIRM,
                firm.getId(),
                "Firm created: " + firm.getLawFirmCode() + " (" + firm.getName() + ")",
                null
        );
        auditService.logExplicit(
                firm.getId(),
                admin.getId(),
                "S",
                AuditAction.USER_CREATED,
                AuditEntity.USER,
                admin.getId(),
                "Firm Admin created: " + admin.getUsername() + " for firm: " + firm.getLawFirmCode(),
                null
        );

        return FirmCreationResponse.builder()
                .firmId(firm.getId())
                .lawFirmCode(firm.getLawFirmCode())
                .firmName(firm.getName())
                .adminUserId(admin.getId())
                .adminUsername(admin.getUsername())
                .message("Firm created successfully. Share lawFirmCode and credentials with admin.")
                .build();
    }

    @Override
    @Transactional
    public void suspendFirm(UUID firmId) {
        Firm firm = firmRepository.findById(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));
        firm.setStatus(FirmStatus.SUSPENDED);
        firmRepository.save(firm);
        auditService.logExplicit(
                firmId, null, "S",
                AuditAction.FIRM_SUSPENDED, AuditEntity.FIRM, firmId,
                "Firm suspended: " + firm.getLawFirmCode(), null
        );
        log.info("Firm {} suspended", firm.getLawFirmCode());
    }

    @Override
    @Transactional
    public void activateFirm(UUID firmId) {
        Firm firm = firmRepository.findById(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));
        // Restore to ACTIVE (or TRIAL if still within trial window)
        if (Boolean.TRUE.equals(firm.getIsTrial()) && firm.getTrialExpiresAt() != null
                && firm.getTrialExpiresAt().isAfter(LocalDateTime.now())) {
            firm.setStatus(FirmStatus.TRIAL);
        } else {
            firm.setStatus(FirmStatus.ACTIVE);
        }
        firmRepository.save(firm);
        auditService.logExplicit(
                firmId, null, "S",
                AuditAction.FIRM_ACTIVATED, AuditEntity.FIRM, firmId,
                "Firm activated: " + firm.getLawFirmCode(), null
        );
        log.info("Firm {} activated", firm.getLawFirmCode());
    }

    @Override
    @Transactional
    public void extendTrial(UUID firmId, int additionalDays) {
        if (additionalDays <= 0) {
            throw new BusinessRuleException("additionalDays must be positive");
        }
        Firm firm = firmRepository.findById(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));
        if (!Boolean.TRUE.equals(firm.getIsTrial())) {
            throw new BusinessRuleException("Firm '" + firm.getLawFirmCode() + "' is not on trial");
        }
        LocalDateTime newExpiry = (firm.getTrialExpiresAt() != null && firm.getTrialExpiresAt().isAfter(LocalDateTime.now()))
                ? firm.getTrialExpiresAt().plusDays(additionalDays)
                : LocalDateTime.now().plusDays(additionalDays);
        firm.setTrialExpiresAt(newExpiry);
        firm.setTrialDays((firm.getTrialDays() != null ? firm.getTrialDays() : 0) + additionalDays);
        // If firm was suspended due to trial expiry, reactivate
        if (firm.getStatus() == FirmStatus.SUSPENDED || firm.getStatus() == FirmStatus.EXPIRED) {
            firm.setStatus(FirmStatus.TRIAL);
        }
        firmRepository.save(firm);
        auditService.logExplicit(
                firmId, null, "S",
                AuditAction.FIRM_TRIAL_EXTENDED, AuditEntity.FIRM, firmId,
                "Trial extended by " + additionalDays + " days for firm: " + firm.getLawFirmCode()
                        + " (new expiry: " + newExpiry + ")", null
        );
        log.info("Trial for firm {} extended by {} days, new expiry: {}", firm.getLawFirmCode(), additionalDays, newExpiry);
    }

    @Override
    @Transactional
    public void convertToPermanent(UUID firmId) {
        Firm firm = firmRepository.findById(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));
        firm.setIsTrial(false);
        firm.setTrialExpiresAt(null);
        // Keep trialStartedAt and trialDays for audit trail
        firm.setStatus(FirmStatus.ACTIVE);
        firmRepository.save(firm);
        auditService.logExplicit(
                firmId, null, "S",
                AuditAction.FIRM_CONVERTED_PERMANENT, AuditEntity.FIRM, firmId,
                "Firm converted to permanent: " + firm.getLawFirmCode(), null
        );
        log.info("Firm {} converted to permanent", firm.getLawFirmCode());
    }

    private void cloneSystemRolesForFirm(Firm firm) {
        List<Role> systemRoles = roleRepository.findByFirmIsNullAndIsSystemTrue();

        if (systemRoles.isEmpty()) {
            log.warn("No system roles found to clone for firm {}. " +
                    "Run DataInitializer or check seeding.", firm.getLawFirmCode());
            return;
        }

        for (Role systemRole : systemRoles) {
            if (RoleCode.SUPER_ADMIN.equals(systemRole.getRoleCode())) {
                log.debug("Skipping SUPER_ADMIN clone for firm {} — super admin is platform-level only",
                        firm.getLawFirmCode());
                continue;
            }

            Role firmRole = new Role();
            firmRole.setFirm(firm);
            firmRole.setRoleName(systemRole.getRoleName());
            firmRole.setRoleCode(systemRole.getRoleCode());
            firmRole.setDescription(systemRole.getDescription());
            firmRole.setIsSystem(false);
            firmRole.setApplicableTo(systemRole.getApplicableTo());
            firmRole.setActive(true);
            firmRole = roleRepository.save(firmRole);

            log.info("Cloned role '{}' for firm '{}' (no permissions — SA assigns via override)",
                    systemRole.getRoleCode(), firm.getLawFirmCode());
        }
    }
}