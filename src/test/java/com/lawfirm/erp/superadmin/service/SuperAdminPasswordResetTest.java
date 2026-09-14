package com.lawfirm.erp.superadmin.service;

import com.lawfirm.erp.auth.mapper.AuthMapper;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.JwtUtil;
import com.lawfirm.erp.auth.security.TotpUtil;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.usermanagement.dto.request.ResetPasswordRequest;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.rbac.mapper.RbacResponseMapper;
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
class SuperAdminPasswordResetTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private FirmRepository firmRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private AuditService auditService;
    @Mock private AuthMapper authMapper;
    @Mock private SystemConfigService systemConfigService;
    @Mock private RbacResponseMapper rbacResponseMapper;
    @Mock private JwtUtil jwtUtil;
    @Mock private TotpUtil totpUtil;
    @Mock private org.springframework.security.authentication.AuthenticationManager authenticationManager;

    @InjectMocks
    private SuperAdminServiceImpl superAdminService;

    private User anyUser;

    @BeforeEach
    void setUp() {
        Firm firm = new Firm();
        firm.setId(UUID.randomUUID());
        firm.setName("Test Firm");

        anyUser = new User();
        anyUser.setId(UUID.randomUUID());
        anyUser.setUsername("advocate1");
        anyUser.setEmail("advocate@test.com");
        anyUser.setFullName("Test Advocate");
        anyUser.setFirm(firm);
        anyUser.setUserType(UserType.FIRM_USER);
    }

    @Test
    @DisplayName("Super Admin can reset password for any user")
    void resetPassword_success() {
        when(userRepository.findById(anyUser.getId())).thenReturn(Optional.of(anyUser));
        when(passwordEncoder.encode("NewPass123!")).thenReturn("$encoded");

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setNewPassword("NewPass123!");

        superAdminService.resetPassword(anyUser.getId(), request);

        verify(passwordEncoder).encode("NewPass123!");
        verify(userRepository).save(anyUser);
        verify(auditService).log(eq(com.lawfirm.erp.common.enums.AuditAction.PASSWORD_CHANGED),
                eq(com.lawfirm.erp.common.enums.AuditEntity.USER),
                eq(anyUser.getId()), anyString());
    }

    @Test
    @DisplayName("Super Admin password reset throws if user not found")
    void resetPassword_userNotFound() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setNewPassword("NewPass123!");

        assertThrows(ResourceNotFoundException.class,
                () -> superAdminService.resetPassword(UUID.randomUUID(), request));
    }

    @Test
    @DisplayName("Super Admin can reset password across different firms")
    void resetPassword_crossFirm() {
        // No firm validation — Super Admin can reset anyone
        Firm firmA = new Firm();
        firmA.setId(UUID.randomUUID());
        User userFirmA = new User();
        userFirmA.setId(UUID.randomUUID());
        userFirmA.setUsername("userA");
        userFirmA.setEmail("userA@firma.com");
        userFirmA.setFullName("User A");
        userFirmA.setFirm(firmA);
        userFirmA.setUserType(UserType.FIRM_USER);

        when(userRepository.findById(userFirmA.getId())).thenReturn(Optional.of(userFirmA));
        when(passwordEncoder.encode("CrossFirmPass!")).thenReturn("$encoded");

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setNewPassword("CrossFirmPass!");

        // Should NOT throw — Super Admin has no firm boundary
        assertDoesNotThrow(() -> superAdminService.resetPassword(userFirmA.getId(), request));
        verify(userRepository).save(userFirmA);
    }
}
