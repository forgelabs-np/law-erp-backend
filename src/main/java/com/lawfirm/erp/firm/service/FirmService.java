package com.lawfirm.erp.firm.service;

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
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.UserRole;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.rbac.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirmService {

    private final FirmRepository firmRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public FirmCreationResponse createFirm(CreateFirmRequest request) {

        // 1. Validate firm code uniqueness
        String firmCode = request.getLawFirmCode().toUpperCase().trim();
        if (firmRepository.existsByLawFirmCode(firmCode)) {
            throw new DuplicateResourceException("Firm code '" + firmCode + "' already exists");
        }

        // 2. Validate admin credentials uniqueness
        if (userRepository.existsByUsernameAndFirmId(request.getAdminUsername(), null)) {
            throw new DuplicateResourceException("Admin username already exists");
        }
        if (userRepository.existsByEmailAndFirmId(request.getAdminEmail(), null)) {
            throw new DuplicateResourceException("Admin email already exists");
        }
        if (userRepository.existsByMobileNoAndFirmId(request.getAdminMobileNo(), null)) {
            throw new DuplicateResourceException("Admin mobile number already exists");
        }

        // 3. Create Firm (REMOVED maxEmployees)
        Firm firm = Firm.builder()
                .lawFirmCode(firmCode)
                .name(request.getName())
                .firmType(request.getFirmType())
                .status(FirmStatus.ACTIVE)
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .jurisdiction(request.getJurisdiction())
                // .maxEmployees(resolveMaxEmployees(...)) ← REMOVED THIS
                .build();
        firm = firmRepository.save(firm);
        log.info("Firm created: {}", firm.getLawFirmCode());

        // 4. Get FIRM_ADMIN system role
        Role firmAdminRole = roleRepository.findByRoleCode("FIRM_ADMIN")
                .orElseThrow(() -> new BusinessRuleException("FIRM_ADMIN role not found in system"));

        // 5. Create Firm Admin User
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

        // 6. Assign role via UserRole
        UserRole userRole = UserRole.builder()
                .user(admin)
                .role(firmAdminRole)
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
}