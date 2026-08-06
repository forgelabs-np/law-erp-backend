package com.lawfirm.erp.superadmin.service;

import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.AuthStatus;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.admin.response.AdminUserResponse;
import com.lawfirm.erp.dto.auth.request.SuperAdminLoginRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.RoleRepository;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private SuperAdminService superAdminService;

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

            LoginResponse response = login(USERNAME, PASSWORD, null);

            assertEquals(AuthStatus.SUCCESS, response.getStatus());
            assertEquals(ACCESS_TOKEN, response.getAccessToken());
            assertEquals(REFRESH_TOKEN, response.getRefreshToken());
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

            LoginResponse response = login(USERNAME, PASSWORD, VALID_TOTP);

            assertEquals(AuthStatus.SUCCESS, response.getStatus());
            assertEquals(ACCESS_TOKEN, response.getAccessToken());
            assertEquals(REFRESH_TOKEN, response.getRefreshToken());
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

            when(userRepository.findAllWithRoleAndFirm(null, null, null))
                    .thenReturn(List.of(superAdmin, firmAdmin));

            List<AdminUserResponse> result = superAdminService.getAllUsersWithRoles(null, null, null);

            assertEquals(2, result.size());

            // Firm admin user → its firm-scoped role + firm
            AdminUserResponse ram = result.stream()
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
            AdminUserResponse sa = result.stream()
                    .filter(r -> USERNAME.equals(r.getUsername()))
                    .findFirst().orElseThrow();
            assertEquals("SUPER_ADMIN", sa.getRoleCode());
            assertEquals("SYSTEM", sa.getFirmCode());

            verify(userRepository).findAllWithRoleAndFirm(null, null, null);
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

            when(userRepository.findAllWithRoleAndFirm(null, null, null))
                    .thenReturn(List.of(orphan));

            List<AdminUserResponse> result = superAdminService.getAllUsersWithRoles(null, null, null);

            assertEquals(1, result.size());
            assertNull(result.get(0).getRoleId());
            assertNull(result.get(0).getRoleName());
            assertNull(result.get(0).getFirmId());
            assertNull(result.get(0).getFirmCode());
        }

        @Test
        @DisplayName("Passes userType/search/firmCode filters to the repository (trimmed)")
        void passesFiltersToRepository() {
            when(userRepository.findAllWithRoleAndFirm(UserType.FIRM_USER, "ram", "APX"))
                    .thenReturn(List.of(superAdmin));

            List<AdminUserResponse> result =
                    superAdminService.getAllUsersWithRoles(UserType.FIRM_USER, "  ram  ", " APX ");

            assertEquals(1, result.size());
            verify(userRepository).findAllWithRoleAndFirm(UserType.FIRM_USER, "ram", "APX");
        }

        @Test
        @DisplayName("Uppercases firmCode before passing it to the repository")
        void uppercasesFirmCode() {
            when(userRepository.findAllWithRoleAndFirm(UserType.CLIENT, "ram", "APX"))
                    .thenReturn(List.of(superAdmin));

            List<AdminUserResponse> result =
                    superAdminService.getAllUsersWithRoles(UserType.CLIENT, "ram", " apx ");

            assertEquals(1, result.size());
            verify(userRepository).findAllWithRoleAndFirm(UserType.CLIENT, "ram", "APX");
        }

        @Test
        @DisplayName("Blank search/firmCode are normalized to null filters")
        void blankFiltersBecomeNull() {
            when(userRepository.findAllWithRoleAndFirm(null, null, null))
                    .thenReturn(List.of(superAdmin));

            superAdminService.getAllUsersWithRoles(null, "   ", "");

            verify(userRepository).findAllWithRoleAndFirm(null, null, null);
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
