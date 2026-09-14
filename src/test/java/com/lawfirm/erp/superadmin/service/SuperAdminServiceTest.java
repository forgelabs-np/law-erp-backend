package com.lawfirm.erp.superadmin.service;

import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.AuthStatus;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.dto.admin.response.AdminUserResponse;
import com.lawfirm.erp.dto.auth.request.SuperAdminLoginRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.RolePermission;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.dto.admin.response.RoleResponse;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.auth.mapper.AuthMapper;
import com.lawfirm.erp.auth.security.JwtUtil;
import com.lawfirm.erp.auth.security.TotpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperAdminServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;
    @Mock private TotpUtil totpUtil;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private FirmRepository firmRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;
    @Mock private AuthMapper authMapper;
    @Mock private SystemConfigService systemConfigService;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private com.lawfirm.erp.rbac.mapper.RbacResponseMapper rbacResponseMapper;

    @InjectMocks
    private SuperAdminServiceImpl superAdminService;

    private static final UUID SUPER_ADMIN_ID = UUID.randomUUID();
    private static final UUID SYSTEM_FIRM_ID = UUID.randomUUID();
    private static final UUID SYSTEM_ROLE_ID = UUID.randomUUID();
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "Pass@123";
    private static final String VALID_TOTP = "123456";
    private static final String MFA_SECRET = "JBSWY3DPEHPK3PXP";
    private static final String ACCESS_TOKEN = "access-jwt-token";
    private static final String REFRESH_TOKEN = "refresh-jwt-token";
    private static final String MFA_TOKEN = "mfa-jwt-token";

    private User superAdmin;
    private Firm systemFirm;
    private Role superAdminRole;

    private static final LoginResponse SUCCESS_RESPONSE = LoginResponse.builder()
            .status(AuthStatus.SUCCESS).accessToken(ACCESS_TOKEN).refreshToken(REFRESH_TOKEN).expiresIn(86400000L).build();
    private static final LoginResponse MFA_SETUP_RESPONSE = LoginResponse.builder()
            .status(AuthStatus.MFA_SETUP_REQUIRED).mfaToken(MFA_TOKEN).mfaQrCodeUri("otpauth://totp/...").mfaManualKey("JBSW Y3DP EHPK 3PXP").build();
    private static final LoginResponse MFA_REQUIRED_RESPONSE = LoginResponse.builder()
            .status(AuthStatus.MFA_REQUIRED).mfaToken(MFA_TOKEN).build();

    @BeforeEach
    void setUp() {
        systemFirm = new Firm();
        systemFirm.setId(SYSTEM_FIRM_ID);
        systemFirm.setLawFirmCode("SYSTEM");

        superAdminRole = new Role();
        superAdminRole.setId(SYSTEM_ROLE_ID);
        superAdminRole.setRoleCode("SUPER_ADMIN");
        superAdminRole.setRoleName("SUPER_ADMIN");

        superAdmin = User.builder()
                .username(USERNAME)
                .password("encoded-password")
                .fullName("Super Admin")
                .email("admin@system.com")
                .userType(UserType.SUPER_ADMIN)
                .firm(systemFirm)
                .role(superAdminRole)
                .build();
        superAdmin.setId(SUPER_ADMIN_ID);
        superAdmin.setActive(true);

        lenient().when(systemConfigService.getGlobal(anyString())).thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("MFA not enabled (legacy flow)")
    class MfaDisabled {

        @Test
        @DisplayName("MFA disabled → issues tokens directly")
        void login_withoutMfa_issuesTokens() {
            superAdmin.setMfaEnabled(false);
            mockFindSuperAdmin();
            mockAuthentication();
            when(jwtUtil.generateAccessToken(superAdmin)).thenReturn(ACCESS_TOKEN);
            when(jwtUtil.generateRefreshToken(superAdmin)).thenReturn(REFRESH_TOKEN);
            when(authMapper.toSuccessResponse(ACCESS_TOKEN, REFRESH_TOKEN)).thenReturn(SUCCESS_RESPONSE);

            LoginResponse response = login(USERNAME, PASSWORD, null);

            assertEquals(AuthStatus.SUCCESS, response.getStatus());
            verify(authenticationManager).authenticate(any());
            verify(jwtUtil).generateAccessToken(superAdmin);
            verify(jwtUtil).generateRefreshToken(superAdmin);
        }
    }

    @Nested
    @DisplayName("MFA enabled but not verified — setup flow")
    class MfaSetup {

        @BeforeEach
        void setUp() {
            superAdmin.setMfaEnabled(true);
            superAdmin.setMfaVerified(false);
            superAdmin.setMfaSecret(null);
        }

        @Test
        @DisplayName("First login → generates secret and returns QR code")
        void login_triggersMfaSetup() {
            mockFindSuperAdmin();
            mockAuthentication();
            when(jwtUtil.generateMfaToken(superAdmin)).thenReturn(MFA_TOKEN);
            when(totpUtil.generateSecret()).thenReturn(MFA_SECRET);
            when(totpUtil.buildQrCodeUri(MFA_SECRET, USERNAME, "SYSTEM")).thenReturn("otpauth://totp/...");
            when(totpUtil.formatSecretForDisplay(MFA_SECRET)).thenReturn("JBSW Y3DP EHPK 3PXP");
            when(authMapper.toMfaSetupResponse(MFA_TOKEN, "otpauth://totp/...", "JBSW Y3DP EHPK 3PXP")).thenReturn(MFA_SETUP_RESPONSE);

            LoginResponse response = login(USERNAME, PASSWORD, null);

            assertEquals(AuthStatus.MFA_SETUP_REQUIRED, response.getStatus());
            assertEquals(MFA_TOKEN, response.getMfaToken());
            assertEquals("otpauth://totp/...", response.getMfaQrCodeUri());
            assertEquals("JBSW Y3DP EHPK 3PXP", response.getMfaManualKey());
            assertNull(response.getAccessToken());

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertEquals(MFA_SECRET, userCaptor.getValue().getMfaSecret());
        }

        @Test
        @DisplayName("Re-login during setup → reuses existing secret")
        void relogin_reusesExistingSecret() {
            superAdmin.setMfaSecret(MFA_SECRET);
            mockFindSuperAdmin();
            mockAuthentication();
            when(jwtUtil.generateMfaToken(superAdmin)).thenReturn(MFA_TOKEN);
            when(totpUtil.buildQrCodeUri(MFA_SECRET, USERNAME, "SYSTEM")).thenReturn("otpauth://totp/...");
            when(totpUtil.formatSecretForDisplay(MFA_SECRET)).thenReturn("JBSW Y3DP EHPK 3PXP");
            when(authMapper.toMfaSetupResponse(MFA_TOKEN, "otpauth://totp/...", "JBSW Y3DP EHPK 3PXP")).thenReturn(MFA_SETUP_RESPONSE);

            LoginResponse response = login(USERNAME, PASSWORD, null);

            assertEquals(AuthStatus.MFA_SETUP_REQUIRED, response.getStatus());
            verify(totpUtil, never()).generateSecret();
            verify(userRepository, atMost(1)).save(any());
        }
    }

    @Nested
    @DisplayName("MFA enabled and verified — challenge + validation flow")
    class MfaChallenge {

        @BeforeEach
        void setUp() {
            superAdmin.setMfaEnabled(true);
            superAdmin.setMfaVerified(true);
            superAdmin.setMfaSecret(MFA_SECRET);
        }

        @Test
        @DisplayName("MFA verified, no totpCode → returns challenge with mfaToken")
        void login_withoutTotp_returnsChallenge() {
            mockFindSuperAdmin();
            mockAuthentication();
            when(jwtUtil.generateMfaToken(superAdmin)).thenReturn(MFA_TOKEN);
            when(authMapper.toMfaRequiredResponse(MFA_TOKEN)).thenReturn(MFA_REQUIRED_RESPONSE);

            LoginResponse response = login(USERNAME, PASSWORD, null);

            assertEquals(AuthStatus.MFA_REQUIRED, response.getStatus());
            assertEquals(MFA_TOKEN, response.getMfaToken());
            assertNull(response.getAccessToken());
        }

        @Test
        @DisplayName("MFA verified, blank totpCode → returns challenge")
        void login_withBlankTotp_returnsChallenge() {
            mockFindSuperAdmin();
            mockAuthentication();
            when(jwtUtil.generateMfaToken(superAdmin)).thenReturn(MFA_TOKEN);
            when(authMapper.toMfaRequiredResponse(MFA_TOKEN)).thenReturn(MFA_REQUIRED_RESPONSE);

            LoginResponse response = login(USERNAME, PASSWORD, "");

            assertEquals(AuthStatus.MFA_REQUIRED, response.getStatus());
        }

        @Test
        @DisplayName("MFA verified, valid totpCode → issues tokens")
        void login_withValidTotp_issuesTokens() {
            mockFindSuperAdmin();
            mockAuthentication();
            when(totpUtil.verify(MFA_SECRET, VALID_TOTP)).thenReturn(true);
            when(jwtUtil.generateAccessToken(superAdmin)).thenReturn(ACCESS_TOKEN);
            when(jwtUtil.generateRefreshToken(superAdmin)).thenReturn(REFRESH_TOKEN);
            when(authMapper.toSuccessResponse(ACCESS_TOKEN, REFRESH_TOKEN)).thenReturn(SUCCESS_RESPONSE);

            LoginResponse response = login(USERNAME, PASSWORD, VALID_TOTP);

            assertEquals(AuthStatus.SUCCESS, response.getStatus());
            verify(auditService).log(AuditAction.LOGIN, AuditEntity.AUTH, SUPER_ADMIN_ID, "Super Admin logged in: admin");
        }

        @Test
        @DisplayName("MFA verified, invalid totpCode → throws BadCredentialsException")
        void login_withInvalidTotp_throws() {
            mockFindSuperAdmin();
            mockAuthentication();
            when(totpUtil.verify(MFA_SECRET, "000000")).thenReturn(false);

            assertThrows(BadCredentialsException.class,
                    () -> login(USERNAME, PASSWORD, "000000"));

            verify(auditService).log(AuditAction.LOGIN_FAILED, AuditEntity.AUTH, SUPER_ADMIN_ID,
                    "Invalid MFA code on super admin login: admin");
            verify(jwtUtil, never()).generateAccessToken(any());
        }
    }

    @Nested
    @DisplayName("Login failure scenarios")
    class LoginFailures {

        @Test
        @DisplayName("Unknown username → BadCredentialsException")
        void unknownUsername_throws() {
            when(userRepository.findByUsernameAndUserType("unknown", UserType.SUPER_ADMIN))
                    .thenReturn(Optional.empty());

            assertThrows(BadCredentialsException.class,
                    () -> login("unknown", PASSWORD, null));
        }

        @Test
        @DisplayName("Disabled account → DisabledException")
        void disabledAccount_throws() {
            superAdmin.setActive(false);
            when(userRepository.findByUsernameAndUserType(USERNAME, UserType.SUPER_ADMIN))
                    .thenReturn(Optional.of(superAdmin));

            assertThrows(DisabledException.class,
                    () -> login(USERNAME, PASSWORD, null));
        }

        @Test
        @DisplayName("Blocked account → LockedException")
        void blockedAccount_throws() {
            superAdmin.setIsBlocked(true);
            when(userRepository.findByUsernameAndUserType(USERNAME, UserType.SUPER_ADMIN))
                    .thenReturn(Optional.of(superAdmin));

            assertThrows(LockedException.class,
                    () -> login(USERNAME, PASSWORD, null));
        }

        @Test
        @DisplayName("Wrong password → AuthenticationManager throws → BadCredentialsException")
        void wrongPassword_throws() {
            mockFindSuperAdmin();
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThrows(BadCredentialsException.class,
                    () -> login(USERNAME, "wrong-pass", null));
        }
    }

    @Nested
    @DisplayName("User → Role view (getAllUsersWithRoles)")
    class UserRoleView {

        private Pageable defaultPageable() {
            return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        @Test
        @DisplayName("Returns every user with its role and firm mapped")
        void returnsUsersWithRoles() {
            Role firmAdminRole = new Role();
            firmAdminRole.setId(UUID.randomUUID());
            firmAdminRole.setRoleName("FIRM_ADMIN");
            firmAdminRole.setRoleCode("FIRM_ADMIN");

            Firm firm = new Firm();
            firm.setId(UUID.randomUUID());
            firm.setLawFirmCode("APX");
            firm.setName("Apex Law");

            User firmAdmin = User.builder()
                    .username("ram.sharma")
                    .fullName("Ram Sharma")
                    .email("ram@apex.com")
                    .mobileNo("9841234567")
                    .userType(UserType.FIRM_USER)
                    .firm(firm)
                    .role(firmAdminRole)
                    .build();
            firmAdmin.setId(UUID.randomUUID());
            firmAdmin.setActive(true);
            firmAdmin.setCreatedAt(LocalDateTime.now());

            superAdmin.setCreatedAt(LocalDateTime.now().minusHours(1));

            when(userRepository.findAllWithRoleAndFirmPaged(null, null, null, defaultPageable()))
                    .thenReturn(new PageImpl<>(List.of(firmAdmin, superAdmin), defaultPageable(), 2));

            PagedResponse<AdminUserResponse> result = superAdminService.getAllUsersWithRoles(null, null, null, 0, 20);

            assertEquals(2, result.getContent().size());

            // Firm admin user → its firm-scoped role + firm
            AdminUserResponse ram = result.getContent().stream()
                    .filter(r -> "ram.sharma".equals(r.getUsername()))
                    .findFirst().orElseThrow();
            assertEquals("Ram Sharma", ram.getFullName());
            assertEquals(UserType.FIRM_USER, ram.getUserType());
            assertTrue(ram.isActive());
            assertEquals(firmAdminRole.getId(), ram.getRoleId());
            assertEquals("FIRM_ADMIN", ram.getRoleCode());
            assertEquals("APX", ram.getFirmCode());
            assertEquals("Apex Law", ram.getFirmName());

            // Super admin user → SUPER_ADMIN role + SYSTEM firm
            AdminUserResponse sa = result.getContent().stream()
                    .filter(r -> USERNAME.equals(r.getUsername()))
                    .findFirst().orElseThrow();
            assertEquals("SUPER_ADMIN", sa.getRoleCode());
            assertEquals("SYSTEM", sa.getFirmCode());

            verify(userRepository).findAllWithRoleAndFirmPaged(null, null, null, defaultPageable());
        }

        @Test
        @DisplayName("Handles users without role or firm gracefully")
        void handlesNullRoleAndFirm() {
            User orphan = User.builder()
                    .username("ghost.user")
                    .fullName("Ghost User")
                    .userType(UserType.CLIENT)
                    .build();
            orphan.setId(UUID.randomUUID());
            orphan.setCreatedAt(LocalDateTime.now());

            when(userRepository.findAllWithRoleAndFirmPaged(null, null, null, defaultPageable()))
                    .thenReturn(new PageImpl<>(List.of(orphan), defaultPageable(), 1));

            PagedResponse<AdminUserResponse> result = superAdminService.getAllUsersWithRoles(null, null, null, 0, 20);

            assertEquals(1, result.getContent().size());
            assertNull(result.getContent().get(0).getRoleId());
            assertNull(result.getContent().get(0).getRoleName());
            assertNull(result.getContent().get(0).getFirmId());
            assertNull(result.getContent().get(0).getFirmCode());
        }

        @Test
        @DisplayName("Passes userType/search/firmCode filters to the repository (trimmed)")
        void passesFiltersToRepository() {
            when(userRepository.findAllWithRoleAndFirmPaged(UserType.FIRM_USER, "ram", "APX", defaultPageable()))
                    .thenReturn(new PageImpl<>(List.of(superAdmin), defaultPageable(), 1));

            PagedResponse<AdminUserResponse> result =
                    superAdminService.getAllUsersWithRoles(UserType.FIRM_USER, "  ram  ", " APX ", 0, 20);

            assertEquals(1, result.getContent().size());
            verify(userRepository).findAllWithRoleAndFirmPaged(UserType.FIRM_USER, "ram", "APX", defaultPageable());
        }

        @Test
        @DisplayName("Uppercases firmCode before passing it to the repository")
        void uppercasesFirmCode() {
            when(userRepository.findAllWithRoleAndFirmPaged(UserType.CLIENT, "ram", "APX", defaultPageable()))
                    .thenReturn(new PageImpl<>(List.of(superAdmin), defaultPageable(), 1));

            PagedResponse<AdminUserResponse> result =
                    superAdminService.getAllUsersWithRoles(UserType.CLIENT, "ram", " apx ", 0, 20);

            assertEquals(1, result.getContent().size());
            verify(userRepository).findAllWithRoleAndFirmPaged(UserType.CLIENT, "ram", "APX", defaultPageable());
        }

        @Test
        @DisplayName("Blank search/firmCode are normalized to null filters")
        void blankFiltersBecomeNull() {
            when(userRepository.findAllWithRoleAndFirmPaged(null, null, null, defaultPageable()))
                    .thenReturn(new PageImpl<>(List.of(superAdmin), defaultPageable(), 1));

            superAdminService.getAllUsersWithRoles(null, "   ", "", 0, 20);

            verify(userRepository).findAllWithRoleAndFirmPaged(null, null, null, defaultPageable());
        }
    }

    @Nested
    @DisplayName("getFirmRoles — SA discoverability of a firm's roles (Phase 1)")
    class GetFirmRoles {

        @Test
        @DisplayName("Unknown firm -> ResourceNotFoundException")
        void unknownFirm_notFound() {
            when(firmRepository.existsById(SYSTEM_FIRM_ID)).thenReturn(false);

            assertThrows(ResourceNotFoundException.class,
                    () -> superAdminService.getFirmRoles(SYSTEM_FIRM_ID));
        }

        @Test
        @DisplayName("Firm roles returned with permissions and user counts")
        void returnsRolesWithPermissionsAndCounts() {
            when(firmRepository.existsById(SYSTEM_FIRM_ID)).thenReturn(true);

            Firm firm = new Firm();
            firm.setId(SYSTEM_FIRM_ID);

            Role role = new Role();
            role.setId(SYSTEM_ROLE_ID);
            role.setRoleName("Paralegal");
            role.setRoleCode("PARALEGAL");
            role.setIsSystem(false);
            role.setFirm(firm);
            when(roleRepository.findByFirmIdAndIsSystemFalse(SYSTEM_FIRM_ID)).thenReturn(List.of(role));

            when(userRepository.countUsersByRoleIds(SYSTEM_FIRM_ID))
                    .thenReturn(List.<Object[]>of(new Object[]{SYSTEM_ROLE_ID, 3L}));

            Permission perm = Permission.builder()
                    .code("CASE_MANAGEMENT:VIEW")
                    .build();
            RolePermission rp = RolePermission.builder()
                    .role(role)
                    .permission(perm)
                    .build();
            when(rolePermissionRepository.findByRoleIdIn(List.of(SYSTEM_ROLE_ID)))
                    .thenReturn(List.of(rp));
            when(rbacResponseMapper.toPermissionResponse(perm)).thenReturn(
                    com.lawfirm.erp.dto.admin.response.PermissionResponse.builder()
                            .code("CASE_MANAGEMENT:VIEW")
                            .build());

            List<RoleResponse> result = superAdminService.getFirmRoles(SYSTEM_FIRM_ID);

            assertEquals(1, result.size());
            assertEquals("PARALEGAL", result.get(0).getCode());
            assertEquals(3, result.get(0).getUserCount());
            assertEquals(1, result.get(0).getPermissions().size());
            assertEquals("CASE_MANAGEMENT:VIEW", result.get(0).getPermissions().get(0).getCode());
        }

        @Test
        @DisplayName("Firm with no roles -> empty list, not error")
        void noRoles_emptyList() {
            when(firmRepository.existsById(SYSTEM_FIRM_ID)).thenReturn(true);
            when(roleRepository.findByFirmIdAndIsSystemFalse(SYSTEM_FIRM_ID)).thenReturn(List.of());

            List<RoleResponse> result = superAdminService.getFirmRoles(SYSTEM_FIRM_ID);

            assertTrue(result.isEmpty());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private void mockFindSuperAdmin() {
        when(userRepository.findByUsernameAndUserType(USERNAME, UserType.SUPER_ADMIN))
                .thenReturn(Optional.of(superAdmin));
    }

    private void mockAuthentication() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken(superAdmin, null, superAdmin.getAuthorities()));
    }

    private LoginResponse login(String username, String password, String totpCode) {
        SuperAdminLoginRequest request = new SuperAdminLoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setTotpCode(totpCode);
        return superAdminService.loginSuperAdmin(request);
    }
}
