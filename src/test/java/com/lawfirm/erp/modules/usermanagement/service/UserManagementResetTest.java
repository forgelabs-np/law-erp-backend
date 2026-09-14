package com.lawfirm.erp.modules.usermanagement.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.auth.request.MfaResetRequest;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.email.service.EmailService;
import com.lawfirm.erp.modules.usermanagement.dto.request.ResetPasswordRequest;
import com.lawfirm.erp.modules.usermanagement.mapper.UserManagementMapper;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserManagementResetTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuditService auditService;
    @Mock private PermissionEvaluator permissionEvaluator;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private UserManagementMapper userManagementMapper;
    @Mock private com.lawfirm.erp.modules.audit.repository.AuditLogRepository auditLogRepository;

    @InjectMocks
    private UserManagementServiceImpl userManagementService;

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private User firmUser;
    private User clientUser;

    @BeforeEach
    void setUp() {
        Firm firm = new Firm();
        firm.setId(FIRM_ID);
        firm.setName("Test Firm");

        firmUser = new User();
        firmUser.setId(USER_ID);
        firmUser.setUsername("advocate1");
        firmUser.setEmail("advocate@test.com");
        firmUser.setFullName("Test Advocate");
        firmUser.setFirm(firm);
        firmUser.setUserType(UserType.FIRM_USER);
        firmUser.setMfaEnabled(false);
        firmUser.setMfaSecret(null);

        // For password reset test — firm user with client type
        clientUser = new User();
        clientUser.setId(UUID.randomUUID());
        clientUser.setUsername("client1");
        clientUser.setEmail("client@test.com");
        clientUser.setFullName("Test Client");
        clientUser.setFirm(firm);
        clientUser.setUserType(UserType.CLIENT);
        firmUser.setMfaEnabled(true);
        firmUser.setMfaSecret("JBSWY3DPEHPK3PXP");

        when(currentUserResolver.getCurrentFirmId()).thenReturn(FIRM_ID);
        when(currentUserResolver.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(currentUserResolver.isSuperAdmin()).thenReturn(false);
    }

    // ── MFA Reset ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Firm Admin can reset MFA for firm user")
    void resetMfa_success() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(firmUser));

        MfaResetRequest request = new MfaResetRequest();
        request.setUserId(USER_ID);
        request.setReason("User lost phone");

        userManagementService.resetMfa(USER_ID, request);

        assertNull(firmUser.getMfaSecret());
        assertFalse(firmUser.getMfaVerified());
        assertTrue(firmUser.getMfaEnabled()); // stays true to force re-setup
        verify(userRepository).save(firmUser);
        verify(auditService).log(eq(com.lawfirm.erp.common.enums.AuditAction.MFA_RESET),
                eq(com.lawfirm.erp.common.enums.AuditEntity.AUTH),
                eq(USER_ID), anyString());
    }

    @Test
    @DisplayName("MFA reset throws if user not found")
    void resetMfa_userNotFound() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        MfaResetRequest request = new MfaResetRequest();
        request.setUserId(UUID.randomUUID());

        assertThrows(ResourceNotFoundException.class,
                () -> userManagementService.resetMfa(UUID.randomUUID(), request));
    }

    @Test
    @DisplayName("MFA reset throws if user not in same firm")
    void resetMfa_wrongFirm() {
        Firm otherFirm = new Firm();
        otherFirm.setId(UUID.randomUUID());
        User otherFirmUser = new User();
        otherFirmUser.setId(USER_ID);
        otherFirmUser.setFirm(otherFirm);
        otherFirmUser.setUserType(UserType.FIRM_USER);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(otherFirmUser));

        MfaResetRequest request = new MfaResetRequest();
        request.setUserId(USER_ID);

        assertThrows(ForbiddenException.class,
                () -> userManagementService.resetMfa(USER_ID, request));
    }

    // ── Password Reset ────────────────────────────────────────────────

    @Test
    @DisplayName("Firm Admin can reset password for firm user")
    void resetPassword_success() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(firmUser));
        when(passwordEncoder.encode("NewPass123!")).thenReturn("$encoded");

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setNewPassword("NewPass123!");

        userManagementService.resetPassword(USER_ID, request);

        verify(passwordEncoder).encode("NewPass123!");
        verify(userRepository).save(firmUser);
        verify(emailService).sendPasswordReset(eq(FIRM_ID), eq(ADMIN_ID),
                eq("advocate@test.com"), eq("Test Advocate"),
                eq("NewPass123!"), anyString());
        verify(auditService).log(eq(com.lawfirm.erp.common.enums.AuditAction.PASSWORD_CHANGED),
                eq(com.lawfirm.erp.common.enums.AuditEntity.USER),
                eq(USER_ID), anyString());
    }

    @Test
    @DisplayName("Password reset throws if user not in same firm")
    void resetPassword_wrongFirm() {
        Firm otherFirm = new Firm();
        otherFirm.setId(UUID.randomUUID());
        User otherFirmUser = new User();
        otherFirmUser.setId(USER_ID);
        otherFirmUser.setFirm(otherFirm);
        otherFirmUser.setUserType(UserType.FIRM_USER);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(otherFirmUser));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setNewPassword("NewPass123!");

        assertThrows(ForbiddenException.class,
                () -> userManagementService.resetPassword(USER_ID, request));
    }

    @Test
    @DisplayName("Password reset works for CLIENT user type")
    void resetPassword_worksForClient() {
        when(userRepository.findById(clientUser.getId())).thenReturn(Optional.of(clientUser));
        when(passwordEncoder.encode("ClientPass123!")).thenReturn("$encoded");

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setNewPassword("ClientPass123!");

        userManagementService.resetPassword(clientUser.getId(), request);

        verify(userRepository).save(clientUser);
        verify(emailService).sendPasswordReset(eq(FIRM_ID), eq(ADMIN_ID),
                eq("client@test.com"), eq("Test Client"),
                eq("ClientPass123!"), eq("Test Firm"));
    }
}
