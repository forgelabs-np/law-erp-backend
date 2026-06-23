package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.DuplicateResourceException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.firm.request.CreateEmployeeRequest;
import com.lawfirm.erp.dto.firm.request.UpdateEmployeeRoleRequest;
import com.lawfirm.erp.dto.firm.response.EmployeeResponse;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.UserRole;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.rbac.repository.UserRoleRepository;
import com.lawfirm.erp.security.CurrentUserResolver;
import com.lawfirm.erp.security.PermissionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final UserRepository userRepository;
    private final FirmRepository firmRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserResolver currentUserResolver;
    private final PermissionEvaluator permissionEvaluator;
    private final AuditService auditService;

    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        UUID firmId = getCurrentFirmId();
        Firm firm = firmRepository.findById(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));

        // Validate uniqueness within firm
        validateUniqueness(firmId, request.getUsername(), request.getEmail(), request.getMobileNo());

        // Validate role belongs to this firm and is NOT a system role
        Role role = validateFirmRole(request.getRoleId(), firmId);

        // Create user
        User user = User.builder()
                .username(request.getUsername())
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
                .build();
        user.setActive(true);
        user = userRepository.save(user);

        // Assign role via UserRole junction
        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .build();
        userRoleRepository.save(userRole);

        // ✅ AUDIT: Employee created
        auditService.log(
                AuditAction.USER_CREATED,
                AuditEntity.USER,
                user.getId(),
                "Employee created: " + user.getUsername() + " (" + role.getRoleCode() + ") in firm: " + firm.getLawFirmCode()
        );

        log.info("Employee created: {} in firm {}", user.getUsername(), firm.getLawFirmCode());

        return toResponse(user, role);
    }

    public List<EmployeeResponse> getAllEmployees() {
        UUID firmId = getCurrentFirmId();
        return userRepository.findByFirmIdAndUserType(firmId, UserType.FIRM_USER)
                .stream()
                .map(user -> toResponse(user, user.getRole()))
                .collect(Collectors.toList());
    }

    public EmployeeResponse getEmployeeById(UUID employeeId) {
        UUID firmId = getCurrentFirmId();
        User user = getUserValidated(employeeId, firmId);
        return toResponse(user, user.getRole());
    }

    @Transactional
    public EmployeeResponse updateEmployee(UUID employeeId, CreateEmployeeRequest request) {
        UUID firmId = getCurrentFirmId();
        User user = getUserValidated(employeeId, firmId);

        String oldEmail = user.getEmail();
        String oldName = user.getFullName();

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getMobileNo() != null) user.setMobileNo(request.getMobileNo());

        user = userRepository.save(user);

        // ✅ AUDIT: Employee updated
        auditService.log(
                AuditAction.USER_UPDATED,
                AuditEntity.USER,
                user.getId(),
                "Employee updated: " + user.getUsername() + " (name: " + oldName + " → " + user.getFullName() + ")"
        );

        log.info("Employee updated: {}", user.getUsername());

        return toResponse(user, user.getRole());
    }

    @Transactional
    public EmployeeResponse updateEmployeeRole(UUID employeeId, UpdateEmployeeRoleRequest request) {
        UUID firmId = getCurrentFirmId();
        User user = getUserValidated(employeeId, firmId);

        String oldRole = user.getRole().getRoleCode();

        // Validate new role
        Role newRole = validateFirmRole(request.getRoleId(), firmId);

        // Remove all existing roles and assign new one
        userRoleRepository.deleteByUserId(employeeId);

        UserRole userRole = UserRole.builder()
                .user(user)
                .role(newRole)
                .build();
        userRoleRepository.save(userRole);

        // Update user's direct role reference (for JWT)
        user.setRole(newRole);
        user = userRepository.save(user);

        // Invalidate cache and bump version
        permissionEvaluator.clearUserCache(user.getId());
        userRepository.incrementPermissionVersion(user.getId());

        // ✅ AUDIT: Role changed
        auditService.log(
                AuditAction.USER_ROLE_CHANGED,
                AuditEntity.USER,
                user.getId(),
                "Role changed for " + user.getUsername() + ": " + oldRole + " → " + newRole.getRoleCode()
        );

        log.info("Role updated for employee {} to {}", user.getUsername(), newRole.getRoleCode());

        return toResponse(user, newRole);
    }

    @Transactional
    public EmployeeResponse toggleEmployeeStatus(UUID employeeId) {
        UUID firmId = getCurrentFirmId();
        User user = getUserValidated(employeeId, firmId);

        boolean newStatus = !user.isActive();
        user.setActive(newStatus);
        user = userRepository.save(user);

        // Bump version when deactivating
        if (!newStatus) {
            userRepository.incrementPermissionVersion(user.getId());
            permissionEvaluator.clearUserCache(user.getId());
        }

        // ✅ AUDIT: Status changed
        auditService.log(
                newStatus ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED,
                AuditEntity.USER,
                user.getId(),
                (newStatus ? "Activated" : "Deactivated") + " employee: " + user.getUsername()
        );

        log.info("Employee {} toggled to {}", user.getUsername(), newStatus ? "active" : "inactive");

        return toResponse(user, user.getRole());
    }

    // ─── Validations ─────────────────────────────────────────────────────────

    private UUID getCurrentFirmId() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) {
            throw new ForbiddenException("This action requires a firm context");
        }
        return firmId;
    }

    private void validateUniqueness(UUID firmId, String username, String email, String mobileNo) {
        if (userRepository.existsByUsernameAndFirmId(username, firmId)) {
            throw new DuplicateResourceException("Username '" + username + "' already exists in your firm");
        }
        if (userRepository.existsByEmailAndFirmId(email, firmId)) {
            throw new DuplicateResourceException("Email '" + email + "' already exists in your firm");
        }
        if (userRepository.existsByMobileNoAndFirmId(mobileNo, firmId)) {
            throw new DuplicateResourceException("Mobile number '" + mobileNo + "' already exists in your firm");
        }
    }

    private Role validateFirmRole(UUID roleId, UUID firmId) {
        if (roleId == null) {
            throw new BusinessRuleException("Role is required");
        }

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        if (role.getFirm() == null) {
            throw new ForbiddenException("Cannot assign system roles directly to employees");
        }

        if (!role.getFirm().getId().equals(firmId)) {
            throw new ForbiddenException("Role does not belong to your firm");
        }

        if (role.getIsSystem() != null && role.getIsSystem()) {
            throw new ForbiddenException("Cannot assign system roles directly to employees");
        }

        if (role.getApplicableTo() != UserType.FIRM_USER) {
            throw new BusinessRuleException("Cannot assign a CLIENT role to an employee");
        }

        if (!role.isActive()) {
            throw new BusinessRuleException("Cannot assign an inactive role");
        }

        return role;
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

    private EmployeeResponse toResponse(User user, Role role) {
        return EmployeeResponse.builder()
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
                .createdAt(user.getCreatedAt())
                .build();
    }
}