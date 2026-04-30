package com.lawfirm.erp.service;

import com.lawfirm.erp.dto.auth.request.LoginRequest;
import com.lawfirm.erp.dto.auth.request.RegisterSuperAdminRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.dto.auth.response.RegisterResponse;
import com.lawfirm.erp.entity.Role;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.repository.RoleRepository;
import com.lawfirm.erp.repository.UserRepository;
import com.lawfirm.erp.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
    private final PasswordEncoder passwordEncoder;

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

        if (userRepository.existsByRoleName("SUPER_ADMIN")) {
            throw new RuntimeException("Super admin already exists. Only one super admin allowed.");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }
        if (userRepository.existsByMobileNo(request.getMobileNo())) {
            throw new RuntimeException("Mobile number already registered");
        }

        Role superAdminRole = roleRepository.findByRoleName("SUPER_ADMIN")
                .orElseThrow(() -> new RuntimeException("SUPER_ADMIN role not found"));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setMobileNo(request.getMobileNo());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRole(superAdminRole);
        user = userRepository.save(user);

        log.info("Super admin registered: {}", user.getUsername());

        return RegisterResponse.builder()
                .userId(user.getId())
                .message("Super admin registration successful!")
                .build();
    }

    public LoginResponse loginSuperAdmin(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!"SUPER_ADMIN".equals(user.getRole().getRoleName())) {
            throw new RuntimeException("Access denied. Not a super admin.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User authenticatedUser = (User) authentication.getPrincipal();
        String accessToken = jwtUtil.generateAccessToken(authenticatedUser);
        String refreshToken = jwtUtil.generateRefreshToken(authenticatedUser);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(86400000L)
                .build();
    }
}