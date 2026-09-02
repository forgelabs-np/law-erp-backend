package com.lawfirm.erp.superadmin.service;

import com.lawfirm.erp.auth.mapper.AuthMapper;
import com.lawfirm.erp.auth.security.JwtUtil;
import com.lawfirm.erp.auth.security.TotpUtil;
import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.admin.request.RolePermissionRequest;
import com.lawfirm.erp.dto.admin.response.AdminUserResponse;
import com.lawfirm.erp.dto.admin.response.RolePermissionResponse;
import com.lawfirm.erp.dto.auth.request.MfaResetRequest;
import com.lawfirm.erp.dto.auth.request.RegisterSuperAdminRequest;
import com.lawfirm.erp.dto.auth.request.SuperAdminLoginRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.dto.auth.response.RegisterResponse;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.RolePermission;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.rbac.repository.RoleRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminServiceImpl implements SuperAdminService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final TotpUtil totpUtil;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FirmRepository firmRepository;
    private final PasswordEncoder passwordEncoder;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final CurrentUserResolver currentUserResolver;
    private final AuditService auditService;
    private final AuthMapper authMapper;

    @Value("${super-admin.registration-secret:}")
    private String superAdminSecret;

    @Override
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
        user.setMfaEnabled(true);
        user.setActive(true);
        user = userRepository.save(user);

        auditService.log(AuditAction.USER_CREATED, AuditEntity.USER, user.getId(),
                "Super Admin registered: " + user.getUsername());

        log.info("Super admin registered: {}", user.getUsername());

        return RegisterResponse.builder()
                .userId(user.getId())
                .message("Super admin registration successful!")
                .build();
    }

    @Override
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

            if (Boolean.TRUE.equals(authenticatedUser.getMfaEnabled())) {
                return handleMfaFlow(authenticatedUser, request.getTotpCode());
            }

            return issueFullTokens(authenticatedUser);

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

    @Override
    public PagedResponse<AdminUserResponse> getAllUsersWithRoles(UserType userType, String search, String firmCode, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> userPage = userRepository.findAllWithRoleAndFirmPaged(
                userType, normalize(search), normalizeFirmCode(firmCode), pageable);

        List<AdminUserResponse> content = userPage.getContent().stream()
                .map(this::toAdminUserResponse)
                .collect(Collectors.toList());

        return PagedResponse.of(userPage, content);
    }

    @Override
    @Transactional
    public void resetMfa(MfaResetRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setMfaSecret(null);
        user.setMfaVerified(false);
        // Keep mfaEnabled=true so user is forced to re-setup MFA on next login
        userRepository.save(user);

        String reason = request.getReason() != null ? request.getReason() : "No reason provided";
        auditService.log(AuditAction.MFA_RESET, AuditEntity.AUTH, user.getId(),
                "MFA reset by Super Admin for: " + user.getUsername() + " (reason: " + reason + ")");

        log.info("MFA reset for user: {} by Super Admin (reason: {})", user.getUsername(), reason);
    }

    @Override
    @Transactional
    public RolePermissionResponse overrideRolePermissions(UUID firmId, UUID roleId, RolePermissionRequest request) {
        UUID adminId = currentUserResolver.getCurrentUserId();

        // Validate firm exists
        Firm firm = firmRepository.findById(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found: " + firmId));

        // Validate role exists and belongs to this firm
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));

        if (role.getFirm() == null || !role.getFirm().getId().equals(firmId)) {
            throw new ForbiddenException("Role does not belong to firm: " + firmId);
        }

        // Super Admin cannot modify system roles via this endpoint (use RoleController for that)
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new ForbiddenException("Cannot modify system roles via firm override. Use /api/v1/admin/roles instead.");
        }

        // Load permissions — no ceiling check, Super Admin can assign anything
        List<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds());
        if (permissions.size() != request.getPermissionIds().size()) {
            throw new ResourceNotFoundException("One or more permission IDs are invalid");
        }

        // Replace permissions
        rolePermissionRepository.deleteByRoleId(role.getId());

        List<RolePermission> newRolePermissions = permissions.stream()
                .map(p -> {
                    RolePermission rp = RolePermission.builder()
                            .role(role)
                            .permission(p)
                            .build();
                    rp.setCreatedBy(adminId);
                    rp.setCreatedAt(LocalDateTime.now());
                    return rp;
                })
                .collect(Collectors.toList());

        rolePermissionRepository.saveAll(newRolePermissions);

        // Invalidate all users holding this role
        List<UUID> affectedUsers = userRepository.findUserIdsByRoleId(role.getId());
        for (UUID userId : affectedUsers) {
            userRepository.incrementPermissionVersion(userId);
        }

        auditService.log(
                AuditAction.ROLE_PERMISSION_CHANGED,
                AuditEntity.ROLE,
                role.getId(),
                "Super Admin overrode permissions for role: " + role.getRoleCode()
                        + " in firm: " + firm.getLawFirmCode()
                        + " (" + permissions.size() + " permissions). "
                        + affectedUsers.size() + " user sessions invalidated."
        );

        log.info("Super Admin overrode {} permissions for role '{}' in firm '{}'. {} users invalidated.",
                permissions.size(), role.getRoleCode(), firm.getLawFirmCode(), affectedUsers.size());

        return RolePermissionResponse.builder()
                .roleId(role.getId())
                .roleName(role.getRoleName())
                .roleCode(role.getRoleCode())
                .permissions(permissions.stream()
                        .map(p -> com.lawfirm.erp.dto.admin.response.PermissionResponse.builder()
                                .id(p.getId())
                                .action(p.getAction())
                                .scope(p.getScope())
                                .code(p.getCode())
                                .description(p.getDescription())
                                .isActive(p.isActive())
                                .createdAt(p.getCreatedAt())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private LoginResponse handleMfaFlow(User user, String totpCode) {
        if (!Boolean.TRUE.equals(user.getMfaVerified())) {
            return buildMfaSetupResponse(user);
        }

        if (totpCode == null || totpCode.isBlank()) {
            return buildMfaChallengeResponse(user);
        }

        if (!totpUtil.verify(user.getMfaSecret(), totpCode)) {
            auditService.log(AuditAction.LOGIN_FAILED, AuditEntity.AUTH, user.getId(),
                    "Invalid MFA code on super admin login: " + user.getUsername());
            throw new BadCredentialsException("Invalid authenticator code");
        }

        return issueFullTokens(user);
    }

    private LoginResponse buildMfaSetupResponse(User user) {
        String mfaToken = jwtUtil.generateMfaToken(user);

        String secret = user.getMfaSecret();
        if (secret == null) {
            secret = totpUtil.generateSecret();
            user.setMfaSecret(secret);
            userRepository.save(user);
        }

        String qrUri = totpUtil.buildQrCodeUri(secret, user.getUsername(), "SYSTEM");

        return authMapper.toMfaSetupResponse(mfaToken, qrUri, totpUtil.formatSecretForDisplay(secret));
    }

    private LoginResponse buildMfaChallengeResponse(User user) {
        String mfaToken = jwtUtil.generateMfaToken(user);
        return authMapper.toMfaRequiredResponse(mfaToken);
    }

    private LoginResponse issueFullTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        auditService.log(AuditAction.LOGIN, AuditEntity.AUTH, user.getId(),
                "Super Admin logged in: " + user.getUsername());

        return authMapper.toSuccessResponse(accessToken, refreshToken);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeFirmCode(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        Role role = user.getRole();
        Firm firm = user.getFirm();
        return AdminUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mobileNo(user.getMobileNo())
                .userType(user.getUserType())
                .isActive(user.isActive())
                .isBlocked(user.getIsBlocked())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .roleId(role != null ? role.getId() : null)
                .roleName(role != null ? role.getRoleName() : null)
                .roleCode(role != null ? role.getRoleCode() : null)
                .firmId(firm != null ? firm.getId() : null)
                .firmCode(firm != null ? firm.getLawFirmCode() : null)
                .firmName(firm != null ? firm.getName() : null)
                .build();
    }
}
