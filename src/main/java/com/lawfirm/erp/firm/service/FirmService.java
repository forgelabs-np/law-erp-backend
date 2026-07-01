package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.DuplicateResourceException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.firm.request.CreateFirmRequest;
import com.lawfirm.erp.dto.firm.response.FirmCreationResponse;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.RolePermission;
import com.lawfirm.erp.rbac.entity.UserRole;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.rbac.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirmService {

    private final FirmRepository firmRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

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

        Firm firm = Firm.builder()
                .lawFirmCode(firmCode)
                .name(request.getName())
                .firmType(request.getFirmType())
                .status(FirmStatus.ACTIVE)
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .jurisdiction(request.getJurisdiction())
                .build();
        firm = firmRepository.save(firm);
        log.info("Firm created: {}", firm.getLawFirmCode());

        auditService.log(
                AuditAction.FIRM_CREATED,
                AuditEntity.FIRM,
                firm.getId(),
                "Firm created: " + firm.getLawFirmCode() + " (" + firm.getName() + ")"
        );

        cloneSystemRolesForFirm(firm);

        Role firmAdminRole = roleRepository.findByRoleCode("FIRM_ADMIN")
                .orElseThrow(() -> new BusinessRuleException("FIRM_ADMIN role not found"));

        User admin = User.builder()
                .username(request.getAdminUsername())
                .email(request.getAdminEmail())
                .mobileNo(request.getAdminMobileNo())
                .password(passwordEncoder.encode(request.getAdminPassword()))
                .fullName(request.getAdminFullName())
                .firm(firm)
                .role(firmAdminRole)
                .userType(UserType.FIRM_USER)
                .isEmailVerified(true)
                .isMobileVerified(true)
                .isBlocked(false)
                .loginAttempts(0)
                .build();
        admin.setActive(true);
        admin = userRepository.save(admin);

        auditService.log(
                AuditAction.USER_CREATED,
                AuditEntity.USER,
                admin.getId(),
                "Firm Admin created: " + admin.getUsername() + " for firm: " + firm.getLawFirmCode()
        );

        Role firmScopedAdminRole = roleRepository
                .findByFirmIdAndRoleCode(firm.getId(), "FIRM_ADMIN")
                .orElseThrow(() -> new BusinessRuleException("Firm-scoped FIRM_ADMIN role not found"));

        UserRole userRole = UserRole.builder()
                .user(admin)
                .role(firmScopedAdminRole)
                .build();
        userRoleRepository.save(userRole);

        log.info("Firm '{}' created with admin '{}'", firm.getLawFirmCode(), admin.getUsername());

        return FirmCreationResponse.builder()
                .firmId(firm.getId())
                .lawFirmCode(firm.getLawFirmCode())
                .firmName(firm.getName())
                .adminUserId(admin.getId())
                .adminUsername(admin.getUsername())
                .message("Firm created successfully. Share lawFirmCode and credentials with admin.")
                .build();
    }

    private void cloneSystemRolesForFirm(Firm firm) {
        List<Role> systemRoles = roleRepository.findByFirmIsNullAndIsSystemTrue();

        if (systemRoles.isEmpty()) {
            log.warn("No system roles found to clone for firm {}", firm.getLawFirmCode());
            return;
        }

        for (Role systemRole : systemRoles) {
            if ("FIRM_ADMIN".equals(systemRole.getRoleCode())) continue;

            Role firmRole = new Role();
            firmRole.setFirm(firm);
            firmRole.setRoleName(systemRole.getRoleName());
            firmRole.setRoleCode(systemRole.getRoleCode());
            firmRole.setDescription(systemRole.getDescription());
            firmRole.setIsSystem(false);
            firmRole.setParentRoleId(systemRole.getId());
            firmRole.setApplicableTo(systemRole.getApplicableTo());
            firmRole.setActive(true);
            firmRole = roleRepository.save(firmRole);

            List<Permission> systemPermissions = rolePermissionRepository
                    .findPermissionsByRoleId(systemRole.getId());

            for (Permission permission : systemPermissions) {
                RolePermission rp = RolePermission.builder()
                        .role(firmRole)
                        .permission(permission)
                        .build();
                rolePermissionRepository.save(rp);
            }

            auditService.log(
                    AuditAction.ROLE_ASSIGNED,
                    AuditEntity.ROLE,
                    firmRole.getId(),
                    "Role cloned: " + systemRole.getRoleCode() + " for firm: " + firm.getLawFirmCode()
            );

            log.info("Cloned role '{}' with {} permissions for firm '{}'",
                    systemRole.getRoleCode(), systemPermissions.size(), firm.getLawFirmCode());
        }
    }
}