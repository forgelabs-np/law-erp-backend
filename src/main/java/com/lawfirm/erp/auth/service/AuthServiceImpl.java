package com.lawfirm.erp.auth.service;

import com.lawfirm.erp.auth.entity.RefreshToken;
import com.lawfirm.erp.auth.mapper.AuthMapper;
import com.lawfirm.erp.auth.repository.RefreshTokenRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.JwtUtil;
import com.lawfirm.erp.auth.security.TotpUtil;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.LoginStatus;
import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.common.service.UserLoginHistoryService;
import com.lawfirm.erp.common.util.PasswordPolicy;
import com.lawfirm.erp.dto.auth.request.ChangePasswordRequest;
import com.lawfirm.erp.dto.auth.request.ForgotPasswordRequest;
import com.lawfirm.erp.dto.auth.request.LoginRequest;
import com.lawfirm.erp.dto.auth.request.MfaSetupConfirmRequest;
import com.lawfirm.erp.dto.auth.request.MfaValidateRequest;
import com.lawfirm.erp.dto.auth.request.PasswordResetRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final JwtUtil jwtUtil;
    private final TotpUtil totpUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final FirmRepository firmRepository;
    private final UserLoginHistoryService loginHistoryService;
    private final AuditService auditService;
    private final AuthMapper authMapper;
    private final EmailService emailService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CurrentUserResolver currentUserResolver;
    private final SystemConfigService systemConfigService;

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

        // A suspended (or expired) firm is cut off entirely — otherwise billing enforcement is
        if (firm.getStatus() == FirmStatus.SUSPENDED || firm.getStatus() == FirmStatus.EXPIRED) {
            throw new BadCredentialsException(
                    "Your firm account has been suspended. Please contact support.");
        }

        User user = (isClient
                ? findClientAccount(request.getUsername(), firm.getId())
                : userRepository.findByUsernameAndFirmId(request.getUsername(), firm.getId()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        // Checked before the password so a locked account cannot burn more attempts. Naming the
        // lock here is safe: the caller is the one whose failed attempts caused it.
        assertNotLocked(user);

        // The password is proved here rather than through AuthenticationManager: its
        // DaoAuthenticationProvider refuses an inactive/blocked account in its pre-authentication
        // checks, before the password is ever compared, which would make every specific reason
        // below unreachable. Proving the password first is also what keeps login from being a
        // username oracle — a reason that names an account state is only shown to a caller who
        // already knows the password.
        if (!passwordMatches(user, request.getPassword())) {
            handleFailedLogin(user);
            throw new BadCredentialsException("Invalid username or password");
        }

        validateAccountStatus(user);

        // Wrong-door and portal checks sit behind the password for the same reason: with the
        // right password the caller is the account owner, so naming the reason is help, not a probe.
        if (isClient && user.getUserType() != UserType.CLIENT) {
            throw new BadCredentialsException("This account is not a client account");
        }
        if (!isClient && user.getUserType() == UserType.SUPER_ADMIN) {
            throw new BadCredentialsException("Super admin must use /super-admin/login");
        }
        // A client account must not slip in through the internal login and bypass the portal switch.
        if (!isClient && user.getUserType() == UserType.CLIENT) {
            throw new BadCredentialsException("Clients must sign in through the client portal");
        }
        if (isClient && !Boolean.TRUE.equals(user.getPortalAccessEnabled())) {
            loginHistoryService.saveRecord(user, LoginStatus.ACCOUNT_LOCKED, "Portal access disabled");
            auditService.log(AuditAction.LOGIN_FAILED, AuditEntity.AUTH, user.getId(),
                    "Client portal access disabled: " + user.getUsername());
            throw new BadCredentialsException(
                    "Client portal access is disabled for your account. Please contact your firm.");
        }

        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        loginHistoryService.saveRecord(user, LoginStatus.LOGIN_SUCCESS, null);

        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            log.info("User {} must change password (first login)", user.getUsername());
            return authMapper.toPasswordChangeRequiredResponse(jwtUtil.generatePasswordChangeToken(user));
        }

        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            return handleMfaFlow(user, request.getTotpCode());
        }

        return issueFullTokens(user);
    }

    private Optional<User> findClientAccount(String identifier, UUID firmId) {

        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }

        String value = identifier.trim();

        return userRepository.findByMobileNoAndFirmId(value, firmId)
                .or(() -> userRepository.findByUsernameAndFirmId(value, firmId));
    }

    // Deliberately NOT @Transactional: the reuse-revoke must survive the BadCredentialsException
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

        // otherwise a stolen refresh token outlives the event meant to kill the session.
        Integer tokenVersion = jwtUtil.extractClaim(refreshToken,
                claims -> claims.get("permVersion", Integer.class));
        Integer dbVersion = userRepository.findPermissionVersionById(userId);
        int currentVersion = dbVersion != null ? dbVersion : 0;
        if (tokenVersion == null || tokenVersion != currentVersion) {
            throw new BadCredentialsException("Your session has ended. Please login again.");
        }

        String jti = jwtUtil.extractTokenId(refreshToken);
        if (jti == null) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        Optional<RefreshToken> row = refreshTokenRepository.findById(jti);
        // An unknown jti is not a valid session: refuse instead of minting tokens off a token
        // that the rotation store has no record of (expired-purged, forged, or already consumed).
        if (row.isEmpty()) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        if (row.get().getUsedAt() != null || row.get().getRevokedAt() != null) {
            revokeRefreshFamily(user);
            throw new BadCredentialsException("Invalid refresh token");
        }
        // Atomic single-use consume — a concurrent refresh cannot win this UPDATE twice, so two
        // token families can never be minted from the same refresh token (rotation cloning).
        int consumed = refreshTokenRepository.markUsed(jti, LocalDateTime.now());
        if (consumed == 0) {
            revokeRefreshFamily(user);
            throw new BadCredentialsException("Invalid refresh token");
        }

        refreshTokenRepository.deleteExpired(LocalDateTime.now());

        auditService.log(AuditAction.TOKEN_REFRESHED, AuditEntity.AUTH, user.getId(),
                "Token refreshed: " + user.getUsername());

        return issueFullTokens(user);
    }

    private void revokeRefreshFamily(User user) {
        LocalDateTime now = LocalDateTime.now();
        List<RefreshToken> family = refreshTokenRepository
                .findByUserIdAndRevokedAtIsNull(user.getId());
        family.forEach(token -> token.setRevokedAt(now));
        refreshTokenRepository.saveAll(family);
        auditService.log(AuditAction.LOGIN_FAILED, AuditEntity.AUTH, user.getId(),
                "Refresh token reuse detected — all sessions revoked for: " + user.getUsername());
    }

    @Override
    @Transactional
    public void logout() {
        UUID userId = currentUserResolver.getCurrentUserId();
        if (userId == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        userRepository.incrementPermissionVersion(userId);

        auditService.log(AuditAction.LOGOUT, AuditEntity.AUTH, user.getId(),
                "Logout: " + user.getUsername());
        log.info("Server-side logout for: {}", user.getUsername());
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
            registerFailedMfaAttempt(user);
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

        validateAccountStatus(user);

        if (!totpUtil.verify(user.getMfaSecret(), request.getTotpCode())) {
            registerFailedMfaAttempt(user);
            auditService.log(AuditAction.LOGIN_FAILED, AuditEntity.AUTH, user.getId(),
                    "Invalid MFA code: " + user.getUsername());
            throw new BadCredentialsException("Invalid authenticator code");
        }

        return issueFullTokens(user);
    }

    @Override
    @Transactional
    public LoginResponse changePassword(ChangePasswordRequest request) {
        String policyViolation = PasswordPolicy.violation(request.getNewPassword());
        if (policyViolation != null) {
            throw new BusinessRuleException(policyViolation);
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessRuleException("Passwords do not match");
        }

        UUID userId = jwtUtil.extractUserIdFromPasswordChangeToken(request.getPasswordChangeToken());
        if (userId == null) {
            throw new BadCredentialsException("Invalid or expired password-change token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // Without this, a captured token stayed usable for its full 10 minutes and could change
        if (!Boolean.TRUE.equals(user.getMustChangePassword())) {
            throw new BadCredentialsException("Invalid or expired password-change token");
        }

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

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        Firm firm = firmRepository.findByLawFirmCode(request.getLawFirmCode().trim().toUpperCase()).orElse(null);
        if (firm == null) {
            log.info("Password recovery requested for unknown firm code");
            return;
        }

        String identifier = request.getUsername().trim();
        User user = userRepository.findByUsernameAndFirmId(identifier, firm.getId())
                .or(() -> userRepository.findByEmailAndFirmId(identifier, firm.getId()))
                .or(() -> userRepository.findByMobileNoAndFirmId(identifier, firm.getId()))
                .orElse(null);

        if (user == null || !user.isActive() || Boolean.TRUE.equals(user.getIsBlocked())) {
            log.info("Password recovery requested for an ineligible account in firm {}", firm.getLawFirmCode());
            return;
        }
        if (user.getUserType() == UserType.CLIENT && !Boolean.TRUE.equals(user.getPortalAccessEnabled())) {
            log.info("Password recovery refused: client portal access disabled for {}", user.getUsername());
            return;
        }

        int validMinutes = 15;
        String token = jwtUtil.generatePasswordResetToken(user);
        emailService.sendPasswordResetLink(firm.getId(), null, user.getEmail(), user.getFullName(),
                token, validMinutes, firm.getName());

        auditService.log(AuditAction.PASSWORD_RESET_REQUESTED, AuditEntity.AUTH, user.getId(),
                "Self-service password reset link issued for: " + user.getUsername());
        log.info("Password reset link issued for: {}", user.getUsername());
    }

    @Override
    @Transactional
    public void resetPasswordWithToken(PasswordResetRequest request) {
        String policyViolation = PasswordPolicy.violation(request.getNewPassword());
        if (policyViolation != null) {
            throw new BusinessRuleException(policyViolation);
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessRuleException("Passwords do not match");
        }

        UUID userId = jwtUtil.extractUserIdFromPasswordResetToken(request.getToken());
        if (userId == null) {
            throw new BadCredentialsException("This reset link is invalid or has expired. Please request a new one.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid reset link"));

        Integer linkVersion = jwtUtil.extractPasswordResetTokenVersion(request.getToken());
        Integer dbVersion = userRepository.findPermissionVersionById(userId);
        int currentVersion = dbVersion != null ? dbVersion : 0;
        if (linkVersion == null || linkVersion != currentVersion) {
            throw new BadCredentialsException(
                    "This reset link has already been used. Please request a new one.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadCredentialsException("Please choose a password you have not used before");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        // Recovering also clears a brute-force lockout — otherwise a locked-out user
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        userRepository.incrementPermissionVersion(userId);

        auditService.log(AuditAction.PASSWORD_RESET, AuditEntity.AUTH, user.getId(),
                "Password reset via self-service link: " + user.getUsername());
        log.info("Self-service password reset completed for: {}", user.getUsername());
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
        assertNotLocked(user);
    }

    // Kept separate from validateAccountStatus so the login flow can refuse a locked account
    // before the password is even checked (and therefore before a failed attempt is counted).
    private void assertNotLocked(User user) {
        long minutes = lockoutMinutesRemaining(user);
        if (minutes > 0) {
            loginHistoryService.saveRecord(user, LoginStatus.ACCOUNT_LOCKED, "Too many attempts");
            throw new BadCredentialsException("Account locked. Try again in " + minutes + " minute(s).");
        }
    }

    /** Minutes left on a brute-force lockout, or 0 when the account is not locked. */
    private long lockoutMinutesRemaining(User user) {
        if (user.getLoginAttempts() == null || user.getLockedUntil() == null
                || user.getLoginAttempts() < systemConfigService.loginMaxAttempts()
                || !user.getLockedUntil().isAfter(LocalDateTime.now())) {
            return 0;
        }
        return Math.max(1, Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes());
    }

    private boolean passwordMatches(User user, String rawPassword) {
        return rawPassword != null && user.getPassword() != null
                && passwordEncoder.matches(rawPassword, user.getPassword());
    }

    // Reuses the password lockout counters so a brute-forced TOTP code is throttled like a
    // brute-forced password — otherwise the 6-digit code can be guessed without limit.
    private void registerFailedMfaAttempt(User user) {
        int maxAttempts = systemConfigService.loginMaxAttempts();
        user.setLoginAttempts(user.getLoginAttempts() + 1);
        if (maxAttempts > 0 && user.getLoginAttempts() >= maxAttempts) {
            user.setLockedUntil(LocalDateTime.now()
                    .plusMinutes(systemConfigService.loginLockMinutes()));
            log.warn("User {} locked after {} failed MFA attempts", user.getUsername(), maxAttempts);
        }
        userRepository.save(user);
    }

    private void handleFailedLogin(User user) {
        int maxLoginAttempts = systemConfigService.loginMaxAttempts();
        user.setLoginAttempts(user.getLoginAttempts() + 1);
        if (user.getLoginAttempts() >= maxLoginAttempts) {
            user.setLockedUntil(LocalDateTime.now()
                    .plusMinutes(systemConfigService.loginLockMinutes()));
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
            registerFailedMfaAttempt(user);
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
        recordRefreshToken(user, refreshToken);

        auditService.log(AuditAction.LOGIN, AuditEntity.AUTH, user.getId(),
                "Login successful: " + user.getUsername());

        return authMapper.toSuccessResponse(accessToken, refreshToken);
    }

    private void recordRefreshToken(User user, String refreshToken) {
        RefreshToken row = new RefreshToken();
        row.setId(jwtUtil.extractTokenId(refreshToken));
        row.setUserId(user.getId());
        row.setExpiresAt(LocalDateTime.now().plusNanos(jwtUtil.getRefreshExpiryMs() * 1_000_000L));
        refreshTokenRepository.save(row);
    }
}
