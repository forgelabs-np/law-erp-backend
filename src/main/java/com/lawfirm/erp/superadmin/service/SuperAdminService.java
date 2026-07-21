package com.lawfirm.erp.superadmin.service;

import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.auth.request.SuperAdminLoginRequest;
import com.lawfirm.erp.dto.auth.request.RegisterSuperAdminRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.dto.auth.response.RegisterResponse;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.auth.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FirmRepository firmRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Value("${super-admin.registration-secret:}")
    private String superAdminSecret;

    @Transactional
    public RegisterResponse registerSuperAdmin(RegisterSuperAdminRequest request) {
        if (superAdminSecret == null || superAdminSecret.isEmpty()) {
            throw new RuntimeException("Super admin registration is disabled");
        }
        if (!superAdminSecret.equals(request.getSecretKey())) {
            throw new RuntimeException("Invalid secret key for super admin registration");
        }

        if (userRepository.existsByUsernameAndUserType(request.getUsername(), UserType.SUPER_ADMIN)) {
            throw new RuntimeException("Super admin already exists. Only one super admin allowed.");
        }

        if (userRepository.existsByUsernameAndFirmId(request.getUsername(), null)) {
            throw new RuntimeException("Username already taken");
        }
        if (userRepository.existsByEmailAndFirmId(request.getEmail(), null)) {
            throw new RuntimeException("Email already registered");
        }
        if (userRepository.existsByMobileNoAndFirmId(request.getMobileNo(), null)) {
            throw new RuntimeException("Mobile number already registered");
        }

        Role superAdminRole = roleRepository.findByRoleName("SUPER_ADMIN")
                .orElseThrow(() -> new RuntimeException("SUPER_ADMIN role not found"));

        Firm systemFirm = firmRepository.findByLawFirmCode("SYSTEM")
                .orElseThrow(() -> new RuntimeException("System firm not found. Run DataInitializer first."));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setMobileNo(request.getMobileNo());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setFirm(systemFirm);
        user.setRole(superAdminRole);
        user.setUserType(UserType.SUPER_ADMIN);
        user.setActive(true);
        user = userRepository.save(user);

        // ✅ AUDIT: Super Admin created
        auditService.log(
                AuditAction.USER_CREATED,
                AuditEntity.USER,
                user.getId(),
                "Super Admin registered: " + user.getUsername()
        );

        log.info("Super admin registered: {}", user.getUsername());

        return RegisterResponse.builder()
                .userId(user.getId())
                .message("Super admin registration successful!")
                .build();
    }

    public LoginResponse loginSuperAdmin(SuperAdminLoginRequest request) {
        try {
            User user = userRepository.findByUsernameAndUserType(request.getUsername(), UserType.SUPER_ADMIN)
                    .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

            if (!user.isActive()) {
                throw new DisabledException("Account is disabled. Please contact system administrator.");
            }

            if (user.getIsBlocked()) {
                throw new LockedException("Account is blocked. Please contact system administrator.");
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), request.getPassword())
            );

            User authenticatedUser = (User) authentication.getPrincipal();
            String accessToken = jwtUtil.generateAccessToken(authenticatedUser);
            String refreshToken = jwtUtil.generateRefreshToken(authenticatedUser);

            // ✅ AUDIT: Super Admin login
            auditService.log(
                    AuditAction.LOGIN,
                    AuditEntity.AUTH,
                    user.getId(),
                    "Super Admin logged in: " + user.getUsername()
            );

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(86400000L)
                    .build();

        } catch (BadCredentialsException e) {
            log.error("Super admin login failed - bad credentials: {}", request.getUsername());
            throw new BadCredentialsException("Invalid username or password");
        } catch (DisabledException e) {
            log.error("Super admin login failed - account disabled: {}", request.getUsername());
            throw new DisabledException("Account is disabled. Please contact system administrator.");
        } catch (LockedException e) {
            log.error("Super admin login failed - account blocked: {}", request.getUsername());
            throw new LockedException("Account is blocked. Please contact system administrator.");
        } catch (AuthenticationException e) {
            log.error("Super admin login failed - authentication error: {}", e.getMessage());
            throw new BadCredentialsException("Invalid username or password");
        }
    }
}