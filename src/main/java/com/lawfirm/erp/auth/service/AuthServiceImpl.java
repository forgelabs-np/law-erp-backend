package com.lawfirm.erp.auth.service;

import com.lawfirm.erp.auth.mapper.AuthMapper;
import com.lawfirm.erp.auth.security.JwtUtil;
import com.lawfirm.erp.auth.security.TotpUtil;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.LoginStatus;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.common.service.UserLoginHistoryService;
import com.lawfirm.erp.dto.auth.request.ChangePasswordRequest;
import com.lawfirm.erp.dto.auth.request.LoginRequest;
import com.lawfirm.erp.dto.auth.request.MfaSetupConfirmRequest;
import com.lawfirm.erp.dto.auth.request.MfaValidateRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final TotpUtil totpUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final FirmRepository firmRepository;
    private final UserLoginHistoryService loginHistoryService;
    private final AuditService auditService;
    private final AuthMapper authMapper;

    @Value("${security.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Override
    @Transactional
    public LoginResponse authenticateInternalUser(LoginRequest request) {
        return performAuthentication(request, false);
    }

    @Override
    @Transactional
    public LoginResponse authenticateClient(LoginRequest request) {
        return performAuthentication(request, true);
    }

    private LoginResponse performAuthentication(LoginRequest request, boolean isClient) {
        if (request.getLawFirmCode() == null || request.getLawFirmCode().isBlank()) {
            throw new BadCredentialsException("Firm code is required");
        }

        Firm firm = firmRepository.findByLawFirmCode(request.getLawFirmCode().trim().toUpperCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        User user = isClient
                ? userRepository.findByMobileNoAndFirmId(request.getUsername(), firm.getId())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"))
                : userRepository.findByUsernameAndFirmId(request.getUsername(), firm.getId())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (isClient && user.getUserType() != UserType.CLIENT) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!isClient && user.getUserType() == UserType.SUPER_ADMIN) {
            throw new BadCredentialsException("Super admin must use /super-admin/login");
        }

        validateAccountStatus(user);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), request.getPassword())
            );
            user.setLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            loginHistoryService.saveRecord(user, LoginStatus.LOGIN_SUCCESS, null);
        } catch (AuthenticationException e) {
            handleFailedLogin(user);
            throw new BadCredentialsException("Invalid username or password");
        }

        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            log.info("User {} must change password (first login)", user.getUsername());
            return authMapper.toPasswordChangeRequiredResponse(jwtUtil.generatePasswordChangeToken(user));
        }

        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            return handleMfaFlow(user, request.getTotpCode());
        }

        return issueFullTokens(user);
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadCredentialsException("Refresh token is required");
        }
        if (!jwtUtil.validateToken(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        UUID userId = jwtUtil.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        auditService.log(AuditAction.TOKEN_REFRESHED, AuditEntity.AUTH, user.getId(),
                "Token refreshed: " + user.getUsername());

        return issueFullTokens(user);
    }

    @Override
    @Transactional
    public LoginResponse confirmMfaSetup(MfaSetupConfirmRequest request) {
        UUID userId = jwtUtil.extractUserIdFromMfaToken(request.getMfaToken());
        if (userId == null) {
            throw new BadCredentialsException("Invalid or expired MFA token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (user.getMfaSecret() == null) {
            throw new BadCredentialsException("MFA setup was not initiated for this account");
        }

        if (!totpUtil.verify(user.getMfaSecret(), request.getTotpCode())) {
            throw new BadCredentialsException("Invalid authenticator code");
        }

        user.setMfaVerified(true);
        userRepository.save(user);

        auditService.log(AuditAction.MFA_ENABLED, AuditEntity.AUTH, user.getId(),
                "MFA setup confirmed: " + user.getUsername());
        log.info("MFA setup confirmed for: {}", user.getUsername());

        return issueFullTokens(user);
    }

    @Override
    public LoginResponse validateMfa(MfaValidateRequest request) {
        UUID userId = jwtUtil.extractUserIdFromMfaToken(request.getMfaToken());
        if (userId == null) {
            throw new BadCredentialsException("Invalid or expired MFA token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (!Boolean.TRUE.equals(user.getMfaVerified()) || user.getMfaSecret() == null) {
            throw new BadCredentialsException("MFA is not set up for this account");
        }

        if (!totpUtil.verify(user.getMfaSecret(), request.getTotpCode())) {
            auditService.log(AuditAction.LOGIN_FAILED, AuditEntity.AUTH, user.getId(),
                    "Invalid MFA code: " + user.getUsername());
            throw new BadCredentialsException("Invalid authenticator code");
        }

        return issueFullTokens(user);
    }

    @Override
    @Transactional
    public LoginResponse changePassword(ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadCredentialsException("Passwords do not match");
        }

        UUID userId = jwtUtil.extractUserIdFromPasswordChangeToken(request.getPasswordChangeToken());
        if (userId == null) {
            throw new BadCredentialsException("Invalid or expired password-change token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        auditService.log(AuditAction.PASSWORD_CHANGED, AuditEntity.AUTH, user.getId(),
                "Password changed on first login: " + user.getUsername());
        log.info("Password changed on first login for: {}", user.getUsername());

        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            return buildMfaResponse(user);
        }
        return issueFullTokens(user);
    }

    private void validateAccountStatus(User user) {
        if (!user.isActive()) {
            loginHistoryService.saveRecord(user, LoginStatus.LOGIN_FAILED, "Account inactive");
            throw new BadCredentialsException("Your account is inactive. Contact your firm admin.");
        }
        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            loginHistoryService.saveRecord(user, LoginStatus.ACCOUNT_LOCKED, "Account blocked");
            throw new BadCredentialsException("Your account has been blocked. Contact support.");
        }
        if (user.getLoginAttempts() >= maxLoginAttempts
                && user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            loginHistoryService.saveRecord(user, LoginStatus.ACCOUNT_LOCKED, "Too many attempts");
            throw new BadCredentialsException("Account locked. Try again in 30 minutes.");
        }
    }

    private void handleFailedLogin(User user) {
        user.setLoginAttempts(user.getLoginAttempts() + 1);
        if (user.getLoginAttempts() >= maxLoginAttempts) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
            log.warn("User {} locked after {} failed attempts", user.getUsername(), maxLoginAttempts);
        }
        userRepository.save(user);
        loginHistoryService.saveRecord(user, LoginStatus.LOGIN_FAILED, "Invalid password");
        auditService.log(AuditAction.LOGIN_FAILED, AuditEntity.AUTH, user.getId(),
                "Failed login: " + user.getUsername());
    }

    private LoginResponse handleMfaFlow(User user, String totpCode) {
        if (!Boolean.TRUE.equals(user.getMfaVerified())) {
            return buildMfaResponse(user);
        }
        if (totpCode == null || totpCode.isBlank()) {
            return buildMfaResponse(user);
        }
        if (!totpUtil.verify(user.getMfaSecret(), totpCode)) {
            auditService.log(AuditAction.LOGIN_FAILED, AuditEntity.AUTH, user.getId(),
                    "Invalid MFA code on login: " + user.getUsername());
            throw new BadCredentialsException("Invalid authenticator code");
        }
        return issueFullTokens(user);
    }

    private LoginResponse buildMfaResponse(User user) {
        String mfaToken = jwtUtil.generateMfaToken(user);

        if (!Boolean.TRUE.equals(user.getMfaVerified())) {
            String secret = user.getMfaSecret();
            if (secret == null) {
                secret = totpUtil.generateSecret();
                user.setMfaSecret(secret);
                userRepository.save(user);
            }

            String qrUri = totpUtil.buildQrCodeUri(
                    secret, user.getUsername(),
                    user.getFirm() != null ? user.getFirm().getLawFirmCode() : "SYSTEM"
            );

            return authMapper.toMfaSetupResponse(mfaToken, qrUri, totpUtil.formatSecretForDisplay(secret));
        }

        log.info("MFA validation required for: {}", user.getUsername());
        return authMapper.toMfaRequiredResponse(mfaToken);
    }

    private LoginResponse issueFullTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        auditService.log(AuditAction.LOGIN, AuditEntity.AUTH, user.getId(),
                "Login successful: " + user.getUsername());

        return authMapper.toSuccessResponse(accessToken, refreshToken);
    }
}
