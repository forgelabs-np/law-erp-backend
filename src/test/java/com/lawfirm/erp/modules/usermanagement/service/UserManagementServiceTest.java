package com.lawfirm.erp.modules.usermanagement.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.modules.audit.repository.AuditLogRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.email.service.EmailService;
import com.lawfirm.erp.modules.usermanagement.dto.request.BulkRoleChangeRequest;
import com.lawfirm.erp.modules.usermanagement.dto.response.BulkOperationResult;
import com.lawfirm.erp.modules.usermanagement.mapper.UserManagementMapper;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditService auditService;
    @Mock private PermissionEvaluator permissionEvaluator;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private UserManagementMapper userManagementMapper;

    @InjectMocks
    private UserManagementServiceImpl userManagementService;

    private static final UUID FIRM_ID = UUID.randomUUID();

    private Role firmScopedRole(String code) {
        Firm firm = new Firm();
        firm.setId(FIRM_ID);
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setFirm(firm);
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setIsSystem(false);
        role.setApplicableTo(UserType.FIRM_USER);
        role.setActive(true);
        return role;
    }

    @BeforeEach
    void seedFirmContext() {
        when(currentUserResolver.getCurrentFirmId()).thenReturn(FIRM_ID);
    }

    @Test
    @DisplayName("Bulk role change to FIRM_ADMIN is forbidden for a firm admin")
    void bulkRoleChangeToFirmAdminIsForbidden() {
        Role firmAdminRole = firmScopedRole("FIRM_ADMIN");
        when(roleRepository.findById(any())).thenReturn(Optional.of(firmAdminRole));

        BulkRoleChangeRequest request = new BulkRoleChangeRequest();
        request.setRoleId(firmAdminRole.getId());
        request.setUserIds(List.of(UUID.randomUUID()));

        assertThrows(ForbiddenException.class,
                () -> userManagementService.bulkRoleChange(request),
                "FIRM_ADMIN must not be assignable via bulk role change by a firm admin");
    }

    @Test
    @DisplayName("Bulk role change to ADVOCATE proceeds past the role validation")
    void bulkRoleChangeToAdvocateProceeds() {
        Role advocateRole = firmScopedRole("ADVOCATE");
        when(roleRepository.findById(any())).thenReturn(Optional.of(advocateRole));

        User employee = new User();
        employee.setId(UUID.randomUUID());
        employee.setFirm(new Firm() {{ setId(FIRM_ID); }});
        employee.setUserType(UserType.FIRM_USER);
        employee.setUsername("advocate");
        employee.setActive(true);
        when(userRepository.findAllById(any())).thenReturn(List.of(employee));
        when(userRepository.save(any())).thenReturn(employee);

        BulkRoleChangeRequest request = new BulkRoleChangeRequest();
        request.setRoleId(advocateRole.getId());
        request.setUserIds(List.of(employee.getId()));

        BulkOperationResult result = userManagementService.bulkRoleChange(request);

        assertEquals(1, result.getSucceeded());
        assertEquals(0, result.getFailed());
    }

    @Test
    @DisplayName("Bulk role change with a non-existent role throws ResourceNotFoundException")
    void bulkRoleChangeUnknownRoleThrows() {
        when(roleRepository.findById(any())).thenReturn(Optional.empty());

        BulkRoleChangeRequest request = new BulkRoleChangeRequest();
        request.setRoleId(UUID.randomUUID());
        request.setUserIds(List.of(UUID.randomUUID()));

        assertThrows(ResourceNotFoundException.class,
                () -> userManagementService.bulkRoleChange(request));
    }
}
