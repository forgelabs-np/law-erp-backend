package com.lawfirm.erp.auth.service;

import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.enums.FirmType;
import com.lawfirm.erp.common.enums.LoginStatus;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.common.service.UserLoginHistoryService;
import com.lawfirm.erp.dto.auth.request.LoginRequest;
import com.lawfirm.erp.dto.auth.request.RegisterClientRequest;
import com.lawfirm.erp.dto.auth.request.RegisterSoloRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.dto.auth.response.RegisterResponse;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.security.JwtUtil;
import com.lawfirm.erp.security.SubdomainGenerator;
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
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FirmRepository firmRepository;
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
        Firm firm;

        if (request.getLawFirmCode() == null || request.getLawFirmCode().trim().isEmpty()) {
            throw new BadCredentialsException("Firm code is required");
        }

        firm = firmRepository.findByLawFirmCode(request.getLawFirmCode())
                .orElseThrow(() -> new BadCredentialsException("Invalid firm code"));

        if (authRoleType == AuthRoleType.CLIENT) {
            user = userRepository.findByMobileNoAndFirmId(request.getUsername(), firm.getId())
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
            if (user.getUserType() != UserType.CLIENT) {
                throw new RuntimeException("You are not authorized to perform this operation");
            }
        } else {
            user = userRepository.findByUsernameAndFirmId(request.getUsername(), firm.getId())
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
            if (user.getUserType() == UserType.SUPER_ADMIN) {
                throw new RuntimeException("Super admin must use /super-admin/login endpoint");
            }
        }

        if (!user.isActive()) {
            loginHistoryService.saveRecord(user, LoginStatus.LOGIN_FAILED, "Account inactive");
            throw new RuntimeException("Your account is inactive. Please contact support.");
        }

        if (user.getIsBlocked()) {
            loginHistoryService.saveRecord(user, LoginStatus.ACCOUNT_LOCKED, "Account blocked");
            throw new RuntimeException("Your account has been blocked. Please contact support.");
        }

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

    @Transactional
    public RegisterResponse registerSolo(RegisterSoloRequest request) {
        Pattern validMobilePattern = Pattern.compile("^(984|985|986|987|988|980|981|982|983)[0-9]{7}$");
        if (!validMobilePattern.matcher(request.getMobileNo()).matches()) {
            throw new RuntimeException("Invalid Nepal mobile number");
        }

        Pattern barCouncilPattern = Pattern.compile("^[A-Z]{3}-[0-9]{4,6}$");
        if (!barCouncilPattern.matcher(request.getBarCouncilNumber()).matches()) {
            throw new RuntimeException("Bar council number must be format: XXX-12345");
        }

        String firmCode = SubdomainGenerator.generate(request.getUsername());
        Firm firm = Firm.builder()
                .lawFirmCode(firmCode)
                .name(request.getFullName() + " Law")
                .firmType(FirmType.SOLO)
                .status(FirmStatus.ACTIVE)
                .email(request.getEmail())
                .phone(request.getMobileNo())
                .build();
        firm = firmRepository.save(firm);

        Role role = roleRepository.findByRoleCode("FIRM_ADMIN")
                .orElseThrow(() -> new RuntimeException("FIRM_ADMIN role not found"));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setMobileNo(request.getMobileNo());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setFirm(firm);
        user.setRole(role);
        user.setUserType(UserType.FIRM_USER);
        user = userRepository.save(user);

        log.info("Registered solo lawyer: {} with firm: {}", user.getUsername(), firm.getLawFirmCode());

        return RegisterResponse.builder()
                .userId(user.getId())
                .message("Registration successful!")
                .build();
    }

    @Transactional
    public RegisterResponse registerClient(RegisterClientRequest request) {
        Firm firm;

        if (request.getLawyerSubdomain() != null && !request.getLawyerSubdomain().isEmpty()) {
            firm = firmRepository.findByLawFirmCode(request.getLawyerSubdomain())
                    .orElseThrow(() -> new RuntimeException("Firm not found with code: " + request.getLawyerSubdomain()));
        } else {
            String firmCode = SubdomainGenerator.generate(request.getUsername());
            firm = Firm.builder()
                    .lawFirmCode(firmCode)
                    .name(request.getFullName())
                    .firmType(FirmType.SOLO)
                    .status(FirmStatus.ACTIVE)
                    .email(request.getEmail())
                    .phone(request.getMobileNo())
                    .build();
            firm = firmRepository.save(firm);
        }

        Role clientRole = roleRepository.findByRoleCode("CLIENT")
                .orElseThrow(() -> new RuntimeException("CLIENT role not found"));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setMobileNo(request.getMobileNo());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setFirm(firm);
        user.setRole(clientRole);
        user.setUserType(UserType.CLIENT);
        user = userRepository.save(user);

        log.info("Registered client: {} under firm: {}", user.getUsername(), firm.getLawFirmCode());

        return RegisterResponse.builder()
                .userId(user.getId())
                .message("Client registration successful!")
                .build();
    }

    public LoginResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new RuntimeException("Refresh token is required");
        }

        if (!jwtUtil.validateToken(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        UUID userId = jwtUtil.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newAccessToken = jwtUtil.generateAccessToken(user);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .expiresIn(86400000L)
                .build();
    }

    private enum AuthRoleType {
        INTERNAL, CLIENT
    }
}