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
import com.lawfirm.erp.dto.firm.request.UpdateFirmRequest;
import com.lawfirm.erp.dto.firm.response.FirmCreationResponse;
import com.lawfirm.erp.dto.firm.response.FirmListResponse;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

        if (isTrial) {
            // Without a fallback a trial firm could be created with no expiry, and
            // TrialExpiryScheduler skips null expiry — so the trial would never end.
            int trialDays = request.getTrialDays() != null && request.getTrialDays() > 0
                    ? request.getTrialDays()
                    : systemConfigService.trialDefaultDays();
            firm.setTrialDays(trialDays);
            firm.setTrialStartedAt(LocalDateTime.now());
            firm.setTrialExpiresAt(LocalDateTime.now().plusDays(trialDays));
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
    public List<FirmListResponse> getAllFirms() {
        return firmRepository.findAll().stream()
                .map(this::toListResponse)
                .collect(Collectors.toList());
    }

    /**
     * Updates a firm's own details and, optionally, its FIRM_ADMIN contact details.
     *
     * <p>Null/blank fields are skipped rather than written, so a partially filled edit form cannot
     * wipe the columns it left empty. The firm code, the admin username and the admin password are
     * immutable here — see {@link #rejectImmutableChanges}.
     */
    @Override
    @Transactional
    public FirmListResponse updateFirm(UUID firmId, UpdateFirmRequest request) {
        Firm firm = firmRepository.findById(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));

        User admin = primaryFirmAdmin(firmId);
        rejectImmutableChanges(firm, admin, request);

        if (hasText(request.getName())) firm.setName(request.getName().trim());
        if (request.getFirmType() != null) firm.setFirmType(request.getFirmType());
        if (request.getEmail() != null) firm.setEmail(trimToNull(request.getEmail()));
        if (request.getPhone() != null) firm.setPhone(trimToNull(request.getPhone()));
        if (request.getAddress() != null) firm.setAddress(trimToNull(request.getAddress()));
        if (request.getJurisdiction() != null) firm.setJurisdiction(trimToNull(request.getJurisdiction()));
        if (request.getLogoUrl() != null) firm.setLogoUrl(trimToNull(request.getLogoUrl()));
        firmRepository.save(firm);

        applyAdminDetails(firm, admin, request);

        auditService.logExplicit(
                firm.getId(),
                admin != null ? admin.getId() : null,
                "S",
                AuditAction.FIRM_UPDATED,
                AuditEntity.FIRM,
                firm.getId(),
                "Firm updated: " + firm.getLawFirmCode() + " (" + firm.getName() + ")",
                null
        );
        log.info("Firm {} updated", firm.getLawFirmCode());

        return toListResponse(firm);
    }

    /**
     * Rejects attempts to change the three fields this endpoint does not own. The console replays
     * the whole create body, so an <i>unchanged</i> value must pass — only a different one is an
     * error. A dropped password change would look exactly like a successful update.
     */
    private void rejectImmutableChanges(Firm firm, User admin, UpdateFirmRequest request) {
        if (hasText(request.getLawFirmCode())
                && !firm.getLawFirmCode().equalsIgnoreCase(request.getLawFirmCode().trim())) {
            throw new BusinessRuleException(
                    "Law firm code cannot be changed after creation (current: " + firm.getLawFirmCode() + ")");
        }
        if (hasText(request.getAdminUsername()) && admin != null
                && !admin.getUsername().equalsIgnoreCase(request.getAdminUsername().trim())) {
            throw new BusinessRuleException(
                    "Firm admin username cannot be changed after creation (current: " + admin.getUsername() + ")");
        }
        if (hasText(request.getAdminPassword())) {
            throw new BusinessRuleException(
                    "The firm admin password cannot be changed here — use "
                            + "POST /api/v1/super-admin/users/{userId}/reset-password");
        }
    }

    /** Admin contact details only; a no-op when the payload carries none of them. */
    private void applyAdminDetails(Firm firm, User admin, UpdateFirmRequest request) {
        boolean detailsSent = hasText(request.getAdminFullName())
                || hasText(request.getAdminEmail())
                || hasText(request.getAdminMobileNo());
        if (!detailsSent) {
            return;
        }
        if (admin == null) {
            throw new BusinessRuleException("No " + RoleCode.FIRM_ADMIN
                    + " account exists for firm '" + firm.getLawFirmCode()
                    + "' — admin details cannot be updated");
        }

        if (hasText(request.getAdminFullName())) {
            admin.setFullName(request.getAdminFullName().trim());
        }
        if (hasText(request.getAdminEmail())) {
            String email = request.getAdminEmail().trim();
            if (!email.equalsIgnoreCase(admin.getEmail())) {
                // Same firm scope as employee creation: the checks exclude nobody, so they run only
                // when the value actually changes (otherwise an unchanged email collides with itself).
                if (userRepository.existsByEmailAndFirmId(email, admin.getFirmId())) {
                    throw new DuplicateResourceException("Admin email already exists in this firm");
                }
                admin.setEmail(email);
            }
        }
        if (hasText(request.getAdminMobileNo())) {
            String mobileNo = request.getAdminMobileNo().trim();
            if (!mobileNo.equals(admin.getMobileNo())) {
                if (userRepository.existsByMobileNoAndFirmId(mobileNo, admin.getFirmId())) {
                    throw new DuplicateResourceException("Admin mobile number already exists in this firm");
                }
                admin.setMobileNo(mobileNo);
            }
        }

        userRepository.save(admin);
        log.info("Firm admin {} contact details updated", admin.getUsername());
    }

    /**
     * The firm's FIRM_ADMIN account — the one created alongside the firm. More than one user can
     * hold FIRM_ADMIN (an employee promoted to it), so the oldest wins: repeated edits keep hitting
     * the same account. Null when the firm has none.
     */
    private User primaryFirmAdmin(UUID firmId) {
        return userRepository.findFirmAdminsByFirmId(firmId).stream()
                .min(Comparator.comparing(User::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(User::getId))
                .orElse(null);
    }

    private FirmListResponse toListResponse(Firm firm) {
        return FirmListResponse.builder()
                .id(firm.getId())
                .lawFirmCode(firm.getLawFirmCode())
                .name(firm.getName())
                .firmType(firm.getFirmType())
                .status(firm.getStatus())
                .email(firm.getEmail())
                .phone(firm.getPhone())
                .address(firm.getAddress())
                .jurisdiction(firm.getJurisdiction())
                .isTrial(Boolean.TRUE.equals(firm.getIsTrial()))
                .trialDays(firm.getTrialDays())
                .trialExpiresAt(firm.getTrialExpiresAt())
                .createdAt(firm.getCreatedAt())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Blank input clears the column (the console sends "" when the operator empties a field). */
    private String trimToNull(String value) {
        String trimmed = value == null ? null : value.trim();
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
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