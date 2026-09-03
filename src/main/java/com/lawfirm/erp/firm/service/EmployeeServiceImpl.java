package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.auth.security.UsernameGenerator;
import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.DuplicateResourceException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.firm.request.CreateEmployeeRequest;
import org.springframework.data.domain.Page;
import com.lawfirm.erp.dto.firm.request.UpdateEmployeeRequest;
import com.lawfirm.erp.dto.firm.request.UpdateEmployeeRoleRequest;
import com.lawfirm.erp.dto.firm.response.EmployeeResponse;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.EmployeeProfile;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.EmployeeProfileRepository;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.email.service.EmailService;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.UserRole;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.rbac.repository.UserRoleRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.PermissionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {

    private final UserRepository userRepository;
    private final FirmRepository firmRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final EmployeeProfileRepository employeeProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserResolver currentUserResolver;
    private final PermissionEvaluator permissionEvaluator;
    private final AuditService auditService;
    private final EmailService emailService;

    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        UUID firmId = getCurrentFirmId();
        Firm firm = firmRepository.findById(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));

        // Validate uniqueness within firm
        validateUniqueness(firmId, request.getEmail(), request.getMobileNo());

        // Validate role
        Role role = validateFirmRole(request.getRoleId(), firmId);
        if ("FIRM_ADMIN".equals(role.getRoleCode())) {
            throw new ForbiddenException("Cannot create employee with FIRM_ADMIN role. Please contact Super Admin.");
        }

        // Create User
        String generatedUsername = generateUniqueUsername(firm.getLawFirmCode(), request.getUsername());

        User user = User.builder()
                .username(generatedUsername)          // ← generated, not admin-typed
                .email(request.getEmail())
                .mobileNo(request.getMobileNo())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .firm(firm)
                .role(role)
                .userType(UserType.FIRM_USER)
                .isEmailVerified(true)
                .isMobileVerified(true)
                .isBlocked(false)
                .loginAttempts(0)
                .mustChangePassword(true)
                // FIX: MFA is now configurable. FIRM_ADMIN always has MFA forced on.
                // ADVOCATE and PARALEGAL can have MFA enabled by firm admin later.
                // "FIRM_ADMIN" is blocked above, so this is always false for employees.
                .mfaEnabled(false)
                .build();

        user.setActive(true);
        user = userRepository.save(user);

        // Assign role via UserRole
        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .build();
        userRoleRepository.save(userRole);

        // Generate employee code
        long count = employeeProfileRepository.countByFirmId(firmId);
        String employeeCode = firm.getLawFirmCode() + "-" + String.format("%04d", count + 1);

        // Create EmployeeProfile
        EmployeeProfile profile = EmployeeProfile.builder()
                .user(user)
                .employeeCode(employeeCode)
                .designation(request.getDesignation())
                .barCouncilNo(request.getBarCouncilNo())
                .specialization(request.getSpecialization())
                .joiningDate(request.getJoiningDate() != null ? request.getJoiningDate() : LocalDate.now())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .notes(request.getNotes())
                .build();
        employeeProfileRepository.save(profile);

        // Audit
        auditService.log(
                AuditAction.USER_CREATED,
                AuditEntity.USER,
                user.getId(),
                "Employee created: " + user.getUsername() + " (" + role.getRoleCode() + ") | Code: " + employeeCode
        );

        log.info("Employee created: {} (code: {}) in firm {}", user.getUsername(), employeeCode, firm.getLawFirmCode());

        // 8. Send welcome email (async, non-blocking)
        UUID currentUserId = currentUserResolver.getCurrentUserId();
        emailService.sendWelcomeEmployee(
                firmId,
                currentUserId,
                user.getEmail(),
                user.getFullName(),
                user.getUsername(),
                request.getPassword(), // raw password before encoding
                firm.getName(),
                firm.getLawFirmCode()
        );

        return toResponse(user, role, profile);
    }

    public PagedResponse<EmployeeResponse> getAllEmployees(int page, int size) {
        UUID firmId = getCurrentFirmId();

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("createdAt").descending()
        );

        Page<User> userPage = userRepository.findByFirmIdAndUserTypePaged(
                firmId,
                UserType.FIRM_USER,
                pageable
        );

        // Batch-load all employee profiles in one query (avoids N+1)
        List<UUID> userIds = userPage.getContent().stream()
                .map(User::getId).collect(Collectors.toList());
        Map<UUID, EmployeeProfile> profileMap = employeeProfileRepository
                .findAllByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        List<EmployeeResponse> content = userPage.getContent().stream()
                .map(user -> toResponse(user, user.getRole(), profileMap.get(user.getId())))
                .collect(Collectors.toList());

        return PagedResponse.of(userPage, content);
    }

    public EmployeeResponse getEmployeeById(UUID employeeId) {
        UUID firmId = getCurrentFirmId();
        User user = getUserValidated(employeeId, firmId);
        EmployeeProfile profile = employeeProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found"));
        return toResponse(user, user.getRole(), profile);
    }

    @Transactional
    public EmployeeResponse updateEmployee(UUID employeeId, UpdateEmployeeRequest request) {
        UUID firmId = getCurrentFirmId();
        User user = getUserValidated(employeeId, firmId);

        // Get the employee profile
        EmployeeProfile profile = employeeProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found"));

        // Only update non-null fields — proper partial update
        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getMobileNo() != null) user.setMobileNo(request.getMobileNo());

        // Handle role update if roleId is provided
        Role newRole = null;
        if (request.getRoleId() != null) {
            newRole = validateFirmRole(request.getRoleId(), firmId);
            String oldRoleCode = user.getRole() != null ? user.getRole().getRoleCode() : "none";

            userRoleRepository.deleteByUserId(employeeId);

            UserRole userRole = UserRole.builder()
                    .user(user)
                    .role(newRole)
                    .build();
            userRoleRepository.save(userRole);

            user.setRole(newRole);

            permissionEvaluator.clearUserCache(user.getId());
            userRepository.incrementPermissionVersion(user.getId());

            auditService.log(
                    AuditAction.USER_ROLE_CHANGED,
                    AuditEntity.USER,
                    user.getId(),
                    "Role changed for " + user.getUsername() + ": " + oldRoleCode + " → " + newRole.getRoleCode()
            );
        }

        user = userRepository.save(user);

        auditService.log(
                AuditAction.USER_UPDATED,
                AuditEntity.USER,
                user.getId(),
                "Employee updated: " + user.getUsername()
        );

        log.info("Employee updated: {}", user.getUsername());
        return toResponse(user, newRole != null ? newRole : user.getRole(), profile);
    }

    @Transactional
    public EmployeeResponse updateEmployeeRole(UUID employeeId, UpdateEmployeeRoleRequest request) {
        UUID firmId = getCurrentFirmId();
        User user = getUserValidated(employeeId, firmId);

        String oldRole = user.getRole().getRoleCode();
        Role newRole = validateFirmRole(request.getRoleId(), firmId);

        userRoleRepository.deleteByUserId(employeeId);

        UserRole userRole = UserRole.builder()
                .user(user)
                .role(newRole)
                .build();
        userRoleRepository.save(userRole);

        user.setRole(newRole);
        user = userRepository.save(user);

        permissionEvaluator.clearUserCache(user.getId());
        userRepository.incrementPermissionVersion(user.getId());

        auditService.log(
                AuditAction.USER_ROLE_CHANGED,
                AuditEntity.USER,
                user.getId(),
                "Role changed for " + user.getUsername() + ": " + oldRole + " → " + newRole.getRoleCode()
        );

        EmployeeProfile profile = employeeProfileRepository.findByUserId(user.getId()).orElse(null);
        return toResponse(user, newRole, profile);
    }

    @Transactional
    public EmployeeResponse toggleEmployeeStatus(UUID employeeId) {
        UUID firmId = getCurrentFirmId();
        User user = getUserValidated(employeeId, firmId);

        boolean newStatus = !user.isActive();
        user.setActive(newStatus);
        user = userRepository.save(user);

        if (!newStatus) {
            userRepository.incrementPermissionVersion(user.getId());
            permissionEvaluator.clearUserCache(user.getId());
        }

        auditService.log(
                newStatus ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED,
                AuditEntity.USER,
                user.getId(),
                (newStatus ? "Activated" : "Deactivated") + " employee: " + user.getUsername()
        );

        EmployeeProfile profile = employeeProfileRepository.findByUserId(user.getId()).orElse(null);
        return toResponse(user, user.getRole(), profile);
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    private UUID getCurrentFirmId() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) {
            throw new ForbiddenException("This action requires a firm context");
        }
        return firmId;
    }

    private void validateUniqueness(UUID firmId, String email, String mobileNo) {
//        if (userRepository.existsByUsernameAndFirmId(username, firmId)) {
//            throw new DuplicateResourceException("Username '" + username + "' already exists in your firm");
//        }
        if (userRepository.existsByEmailAndFirmId(email, firmId)) {
            throw new DuplicateResourceException("Email '" + email + "' already exists in your firm");
        }
        if (userRepository.existsByMobileNoAndFirmId(mobileNo, firmId)) {
            throw new DuplicateResourceException("Mobile number '" + mobileNo + "' already exists in your firm");
        }
    }

    private User getUserValidated(UUID userId, UUID firmId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getFirm() == null || !user.getFirm().getId().equals(firmId)) {
            throw new ForbiddenException("User does not belong to your firm");
        }
        if (user.getUserType() != UserType.FIRM_USER) {
            throw new BusinessRuleException("User is not an employee");
        }

        return user;
    }

    private EmployeeResponse toResponse(User user, Role role, EmployeeProfile profile) {
        EmployeeResponse.EmployeeResponseBuilder builder = EmployeeResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .mobileNo(user.getMobileNo())
                .fullName(user.getFullName())
                .userType(user.getUserType())
                .isActive(user.isActive())
                .roleId(role != null ? role.getId() : null)
                .roleName(role != null ? role.getRoleName() : null)
                .roleCode(role != null ? role.getRoleCode() : null)
                .createdAt(user.getCreatedAt());

        if (profile != null) {
            builder.employeeCode(profile.getEmployeeCode())
                    .designation(profile.getDesignation())
                    .barCouncilNo(profile.getBarCouncilNo())
                    .specialization(profile.getSpecialization())
                    .joiningDate(profile.getJoiningDate())
                    .emergencyContactName(profile.getEmergencyContactName())
                    .emergencyContactPhone(profile.getEmergencyContactPhone())
                    .notes(profile.getNotes());
        }

        return builder.build();
    }

    private Role validateFirmRole(UUID roleId, UUID firmId) {
        if (roleId == null) {
            throw new BusinessRuleException("Role is required");
        }

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        // 1. Must be firm-scoped (not system role)
        if (role.getFirm() == null) {
            throw new ForbiddenException("Cannot assign system roles directly to employees");
        }

        // 2. Must belong to this firm
        if (!role.getFirm().getId().equals(firmId)) {
            throw new ForbiddenException("Role does not belong to your firm");
        }

        // 3. Must NOT be a system role
        if (role.getIsSystem() != null && role.getIsSystem()) {
            throw new ForbiddenException("Cannot assign system roles directly to employees");
        }

        // 4. Must NOT be FIRM_ADMIN
        if ("FIRM_ADMIN".equals(role.getRoleCode())) {
            throw new ForbiddenException("Cannot assign FIRM_ADMIN role. Only Super Admin can create Firm Admins.");
        }

        // 5. Must be applicable to FIRM_USER
        if (role.getApplicableTo() != UserType.FIRM_USER) {
            throw new BusinessRuleException("Cannot assign a CLIENT role to an employee");
        }

        // 6. Role must be active
        if (!role.isActive()) {
            throw new BusinessRuleException("Cannot assign an inactive role");
        }

        return role;
    }

    private String generateUniqueUsername(String firmCode, String baseName) {
        String base = UsernameGenerator.build(firmCode, baseName);
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            suffix++;
            candidate = base + suffix;
        }
        return candidate;
    }
}