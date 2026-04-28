package com.lawfirm.erp.service;

import com.lawfirm.erp.dto.auth.request.LoginRequest;
import com.lawfirm.erp.dto.auth.request.RegisterSoloRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.dto.auth.response.RegisterResponse;
import com.lawfirm.erp.entity.Role;
import com.lawfirm.erp.entity.Tenant;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.enums.LoginStatus;
import com.lawfirm.erp.enums.RoleType;
import com.lawfirm.erp.enums.TenantType;
import com.lawfirm.erp.repository.RoleRepository;
import com.lawfirm.erp.repository.TenantRepository;
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
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserLoginHistoryService loginHistoryService;

    @Value("${security.max-login-attempts:5}")
    private int maxLoginAttempts;

    public LoginResponse authenticateInternalUser(LoginRequest request) {
        return performAuthentication(request, AuthRoleType.INTERNAL);
    }

    public LoginResponse authenticateClient(LoginRequest request) {
        return performAuthentication(request, AuthRoleType.CLIENT);
    }

    private LoginResponse performAuthentication(LoginRequest request, AuthRoleType authRoleType) {
        User user;

        // Fetch user based on role type
        if (authRoleType == AuthRoleType.CLIENT) {
            user = userRepository.findByMobileNo(request.getUsername())
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        } else {
            user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        }

        // Validate user account
        validateUserAccount(user);

        // Validate role authorization
        validateRoleAuthorization(user, authRoleType);

        // Check account lock
        if (user.getLoginAttempts() >= maxLoginAttempts && user.getLockedUntil() != null &&
                user.getLockedUntil().isAfter(LocalDateTime.now())) {
            loginHistoryService.saveRecord(user, LoginStatus.ACCOUNT_LOCKED, "Too many failed attempts");
            throw new RuntimeException("Account is temporarily locked. Please try again later.");
        }

        // Authenticate
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), request.getPassword())
            );
            loginHistoryService.saveRecord(user, LoginStatus.LOGIN_SUCCESS, null);

            // Reset login attempts on success
            user.setLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

        } catch (AuthenticationException e) {
            // Increment login attempts on failure
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
        if (!user.getIsActive()) {
            loginHistoryService.saveRecord(user, LoginStatus.LOGIN_FAILED, "Account inactive");
            throw new RuntimeException("Your account is inactive. Please contact support.");
        }
        if (user.getIsBlocked()) {
            loginHistoryService.saveRecord(user, LoginStatus.ACCOUNT_LOCKED, "Account blocked");
            throw new RuntimeException("Your account has been blocked. Please contact support.");
        }
    }

    private void validateRoleAuthorization(User user, AuthRoleType authRoleType) {
        RoleType roleType = user.getRole().getName();

        if (authRoleType == AuthRoleType.CLIENT && roleType != RoleType.CLIENT) {
            throw new RuntimeException("You are not authorized to perform this operation");
        }
        if (authRoleType == AuthRoleType.INTERNAL && roleType == RoleType.CLIENT) {
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

        // Validate Nepal mobile number
        Pattern validMobilePattern = Pattern.compile("^(984|985|986|987|988|980|981|982|983)[0-9]{7}$");
        if (!validMobilePattern.matcher(request.getMobileNo()).matches()) {
            throw new RuntimeException("Invalid Nepal mobile number. Must start with 984,985,986,987,988,980,981,982,983");
        }

        // Validate bar council number format
        Pattern barCouncilPattern = Pattern.compile("^[A-Z]{3}-[0-9]{4,6}$");
        if (!barCouncilPattern.matcher(request.getBarCouncilNumber()).matches()) {
            throw new RuntimeException("Bar council number must be format: XXX-12345 (e.g., NBL-12345)");
        }

        // Create tenant
        String subdomain = SubdomainGenerator.generate(request.getUsername());
        Tenant tenant = new Tenant();
        tenant.setSubdomain(subdomain);
        tenant.setSchemaName("tenant_" + subdomain.replace("-", "_"));
        tenant.setTenantType(TenantType.SOLO);
        tenant.setOrganizationName(request.getFullName());
        tenant.setEmail(request.getEmail());
        tenant.setPhone(request.getMobileNo());
        tenant.setBarCouncilNumber(request.getBarCouncilNumber());
        tenant = tenantRepository.save(tenant);

        // Get role
        Role role = roleRepository.findByName(RoleType.TENANT_ADMIN)
                .orElseThrow(() -> new RuntimeException("Role not found. Run data.sql first"));

        // Create user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setMobileNo(request.getMobileNo());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setTenant(tenant);
        user.setRole(role);
        user = userRepository.save(user);

        log.info("Registered solo lawyer: {} with subdomain: {}", user.getUsername(), tenant.getSubdomain());

        return RegisterResponse.builder()
                .userId(user.getId())
                .message("Registration successful!")
                .build();
    }

    public LoginResponse refreshToken(String refreshToken) {
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

    // Inner enum for authentication type
    private enum AuthRoleType {
        INTERNAL, CLIENT
    }
}