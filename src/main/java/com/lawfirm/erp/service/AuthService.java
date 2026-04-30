// service/AuthService.java
package com.lawfirm.erp.service;

import com.lawfirm.erp.dto.auth.request.LoginRequest;
import com.lawfirm.erp.dto.auth.request.RegisterClientRequest;
import com.lawfirm.erp.dto.auth.request.RegisterSoloRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.dto.auth.response.RegisterResponse;
import com.lawfirm.erp.entity.Role;
import com.lawfirm.erp.entity.TenantType;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.enums.LoginStatus;
import com.lawfirm.erp.repository.RoleRepository;
import com.lawfirm.erp.repository.TenantTypeRepository;
import com.lawfirm.erp.repository.UserRepository;
import com.lawfirm.erp.util.JwtUtil;
import com.lawfirm.erp.util.SubdomainGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantTypeRepository tenantTypeRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserLoginHistoryService loginHistoryService;

    @Value("${security.max-login-attempts}")
    private int maxLoginAttempts;

    public LoginResponse authenticateInternalUser(LoginRequest request) {
        return performAuthentication(request, AuthRoleType.INTERNAL);
    }

    public LoginResponse authenticateClient(LoginRequest request) {
        return performAuthentication(request, AuthRoleType.CLIENT);
    }

    private LoginResponse performAuthentication(LoginRequest request, AuthRoleType authRoleType) {
        User user;

        if (authRoleType == AuthRoleType.CLIENT) {
            user = userRepository.findByMobileNo(request.getUsername())
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        } else {
            user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        }

        validateUserAccount(user);
        validateRoleAuthorization(user, authRoleType);

        if (user.getLoginAttempts() >= maxLoginAttempts && user.getLockedUntil() != null &&
                user.getLockedUntil().isAfter(LocalDateTime.now())) {
            loginHistoryService.saveRecord(user, LoginStatus.ACCOUNT_LOCKED, "Too many failed attempts");
            throw new RuntimeException("Account is temporarily locked. Please try again later.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), request.getPassword())
            );
            loginHistoryService.saveRecord(user, LoginStatus.LOGIN_SUCCESS, null);

            user.setLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

        } catch (AuthenticationException e) {
            user.setLoginAttempts(user.getLoginAttempts() + 1);
            if (user.getLoginAttempts() >= maxLoginAttempts) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
            }
            userRepository.save(user);

            loginHistoryService.saveRecord(user, LoginStatus.LOGIN_FAILED, "Invalid password");
            throw new BadCredentialsException("Invalid username or password");
        }

        User authenticatedUser = (User) authentication.getPrincipal();
        String accessToken = jwtUtil.generateAccessToken(authenticatedUser);
        String refreshToken = jwtUtil.generateRefreshToken(authenticatedUser);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(86400000L)
                .build();
    }

    private void validateUserAccount(User user) {
        if (!user.isActive()) {
            loginHistoryService.saveRecord(user, LoginStatus.LOGIN_FAILED, "Account inactive");
            throw new RuntimeException("Your account is inactive. Please contact support.");
        }
        if (user.getIsBlocked()) {
            loginHistoryService.saveRecord(user, LoginStatus.ACCOUNT_LOCKED, "Account blocked");
            throw new RuntimeException("Your account has been blocked. Please contact support.");
        }
    }

    private void validateRoleAuthorization(User user, AuthRoleType authRoleType) {
        String roleName = user.getRole().getRoleName();

        if (authRoleType == AuthRoleType.INTERNAL && "SUPER_ADMIN".equals(roleName)) {
            throw new RuntimeException("Super admin must use /super-admin/login endpoint");
        }

        if (authRoleType == AuthRoleType.CLIENT && !"CLIENT".equals(roleName)) {
            throw new RuntimeException("You are not authorized to perform this operation");
        }

        if (authRoleType == AuthRoleType.INTERNAL && "CLIENT".equals(roleName)) {
            throw new RuntimeException("You are not authorized to perform this operation");
        }
    }

    @Transactional
    public RegisterResponse registerSolo(RegisterSoloRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }
        if (userRepository.existsByMobileNo(request.getMobileNo())) {
            throw new RuntimeException("Mobile number already registered");
        }

        Pattern validMobilePattern = Pattern.compile("^(984|985|986|987|988|980|981|982|983)[0-9]{7}$");
        if (!validMobilePattern.matcher(request.getMobileNo()).matches()) {
            throw new RuntimeException("Invalid Nepal mobile number. Must start with 984,985,986,987,988,980,981,982,983");
        }

        Pattern barCouncilPattern = Pattern.compile("^[A-Z]{3}-[0-9]{4,6}$");
        if (!barCouncilPattern.matcher(request.getBarCouncilNumber()).matches()) {
            throw new RuntimeException("Bar council number must be format: XXX-12345 (e.g., NBL-12345)");
        }

        // Get SOLO tenant type from database
        TenantType soloType = tenantTypeRepository.findByCode("SOLO")
                .orElseThrow(() -> new RuntimeException("SOLO tenant type not found"));

        // Get FIRM_ADMIN role
        Role role = roleRepository.findByRoleName("FIRM_ADMIN")
                .orElseThrow(() -> new RuntimeException("FIRM_ADMIN role not found"));

        // Create user with tenant type directly
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setMobileNo(request.getMobileNo());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setTenantType(soloType);
        user.setRole(role);
        user = userRepository.save(user);

        log.info("Registered solo lawyer: {} with tenant type: {}", user.getUsername(), soloType.getCode());

        return RegisterResponse.builder()
                .userId(user.getId())
                .message("Registration successful!")
                .build();
    }

    public LoginResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new RuntimeException("Refresh token is required");
        }

        if (!jwtUtil.validateToken(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        Long userId = jwtUtil.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newAccessToken = jwtUtil.generateAccessToken(user);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .expiresIn(86400000L)
                .build();
    }

    @Transactional
    public RegisterResponse registerClient(RegisterClientRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }
        if (userRepository.existsByMobileNo(request.getMobileNo())) {
            throw new RuntimeException("Mobile number already registered");
        }

        TenantType tenantType;

        if (request.getLawyerSubdomain() != null && !request.getLawyerSubdomain().isEmpty()) {
            // Find user by subdomain to get their tenant type
            User lawyer = userRepository.findBySubdomain(request.getLawyerSubdomain())
                    .orElseThrow(() -> new RuntimeException("Lawyer not found with subdomain: " + request.getLawyerSubdomain()));
            tenantType = lawyer.getTenantType();
        } else {
            tenantType = tenantTypeRepository.findByCode("SOLO")
                    .orElseThrow(() -> new RuntimeException("SOLO tenant type not found"));
        }

        Role clientRole = roleRepository.findByRoleName("CLIENT")
                .orElseThrow(() -> new RuntimeException("CLIENT role not found"));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setMobileNo(request.getMobileNo());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setTenantType(tenantType);
        user.setRole(clientRole);
        user = userRepository.save(user);

        log.info("Registered client: {} with tenant type: {}", user.getUsername(), tenantType.getCode());

        return RegisterResponse.builder()
                .userId(user.getId())
                .message("Client registration successful!")
                .build();
    }

    private enum AuthRoleType {
        INTERNAL, CLIENT
    }
}