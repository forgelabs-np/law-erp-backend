package com.lawfirm.erp.modules.usermanagement.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.modules.audit.repository.AuditLogRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.email.service.EmailService;
import com.lawfirm.erp.modules.usermanagement.dto.request.BulkDeactivateRequest;
import com.lawfirm.erp.modules.usermanagement.dto.request.BulkRoleChangeRequest;
import com.lawfirm.erp.modules.usermanagement.dto.request.ResetPasswordRequest;
import com.lawfirm.erp.modules.usermanagement.dto.response.BulkOperationResult;
import com.lawfirm.erp.modules.usermanagement.dto.response.UserPermissionsResponse;
import com.lawfirm.erp.modules.usermanagement.dto.response.UserProfileResponse;
import com.lawfirm.erp.modules.usermanagement.dto.response.UserSummaryResponse;
import com.lawfirm.erp.modules.usermanagement.mapper.UserManagementMapper;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserManagementServiceImpl implements UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final PermissionEvaluator permissionEvaluator;
    private final CurrentUserResolver currentUserResolver;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserManagementMapper userManagementMapper;

    @Override
    public List<UserSummaryResponse> listUsers(UserType userType, UUID roleId, Boolean isActive) {
        UUID firmId = getRequiredFirmId();

        List<User> users;
        if (roleId != null) {
            users = userRepository.findByFirmIdAndRoleId(firmId, roleId);
            if (userType != null) {
                users = users.stream().filter(u -> u.getUserType() == userType).toList();
            }
        } else if (userType != null) {
            users = userRepository.findByFirmIdAndUserType(firmId, userType);
        } else {
            users = userRepository.findByFirmId(firmId);
        }

        if (isActive != null) {
            users = users.stream().filter(u -> u.isActive() == isActive).toList();
        }

        return users.stream().map(userManagementMapper::toSummary).collect(Collectors.toList());
    }

    @Override
    public List<UserSummaryResponse> searchUsers(String query) {
        UUID firmId = getRequiredFirmId();

        if (query == null || query.isBlank()) {
            return listUsers(null, null, null);
        }

        String q = query.toLowerCase().trim();

        return userRepository.findByFirmId(firmId).stream()
                .filter(u ->
                        (u.getFullName() != null && u.getFullName().toLowerCase().contains(q)) ||
                        (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)) ||
                        (u.getUsername() != null && u.getUsername().toLowerCase().contains(q)) ||
                        (u.getMobileNo() != null && u.getMobileNo().contains(q))
                )
                .map(userManagementMapper::toSummary)
                .collect(Collectors.toList());
    }

    @Override
    public UserProfileResponse getUserProfile(UUID userId) {
        UUID firmId = getRequiredFirmId();
        User user = getValidatedUser(userId, firmId);

        List<Permission> permissions = user.getRole() != null
                ? rolePermissionRepository.findPermissionsByRoleId(user.getRole().getId())
                : List.of();

        List<String> permCodes = permissions.stream()
                .map(Permission::getCode)
                .collect(Collectors.toList());

        List<UserProfileResponse.ModulePermissionGroup> byModule = userManagementMapper.groupByModule(permissions);

        List<UserProfileResponse.ActivityEntry> recentActivity = auditLogRepository
                .findByFirmAndUser(firmId, userId, null, null,
                        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(userManagementMapper::toActivityEntry)
                .collect(Collectors.toList());

        LocalDateTime startOfMonth = LocalDateTime.now()
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        long actionsThisMonth = auditLogRepository
                .findByFirmAndUser(firmId, userId, startOfMonth, null,
                        PageRequest.of(0, Integer.MAX_VALUE))
                .getTotalElements();

        Role role = user.getRole();
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mobileNo(user.getMobileNo())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .userType(user.getUserType())
                .isActive(user.isActive())
                .isBlocked(user.getIsBlocked())
                .portalAccessEnabled(user.getPortalAccessEnabled())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .roleId(role != null ? role.getId() : null)
                .roleName(role != null ? role.getRoleName() : null)
                .roleCode(role != null ? role.getRoleCode() : null)
                .permissions(permCodes)
                .permissionsByModule(byModule)
                .recentActivity(recentActivity)
                .actionsThisMonth(actionsThisMonth)
                .build();
    }

    @Override
    public UserPermissionsResponse getUserPermissions(UUID userId) {
        UUID firmId = getRequiredFirmId();
        User user = getValidatedUser(userId, firmId);

        List<Permission> permissions = user.getRole() != null
                ? rolePermissionRepository.findPermissionsByRoleId(user.getRole().getId())
                : List.of();

        Role role = user.getRole();
        return UserPermissionsResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .roleCode(role != null ? role.getRoleCode() : null)
                .roleName(role != null ? role.getRoleName() : null)
                .allPermissions(permissions.stream().map(Permission::getCode).collect(Collectors.toList()))
                .byModule(userManagementMapper.groupByModule(permissions))
                .build();
    }

    @Override
    public List<UserProfileResponse.ActivityEntry> getUserActivity(
            UUID userId, LocalDateTime from, LocalDateTime to, int page, int size) {
        UUID firmId = getRequiredFirmId();
        getValidatedUser(userId, firmId);

        return auditLogRepository
                .findByFirmAndUser(firmId, userId, from, to,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(userManagementMapper::toActivityEntry)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void resetPassword(UUID userId, ResetPasswordRequest request) {
        UUID firmId = getRequiredFirmId();
        User user = getValidatedUser(userId, firmId);

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        userRepository.incrementPermissionVersion(userId);
        permissionEvaluator.clearUserCache(userId);

        auditService.log(AuditAction.PASSWORD_CHANGED, AuditEntity.USER, userId,
                "Password reset by firm admin for: " + user.getUsername());

        log.info("Password reset for: {}", user.getUsername());

        UUID currentUserId = currentUserResolver.getCurrentUserId();
        emailService.sendPasswordReset(
                firmId, currentUserId, user.getEmail(), user.getFullName(),
                request.getNewPassword(),
                user.getFirm() != null ? user.getFirm().getName() : "Your Firm"
        );
    }

    @Override
    @Transactional
    public BulkOperationResult bulkDeactivate(BulkDeactivateRequest request) {
        UUID firmId = getRequiredFirmId();
        UUID currentUserId = currentUserResolver.getCurrentUserId();

        // Batch-load all users in one query (avoids N+1)
        List<User> users = userRepository.findAllById(request.getUserIds());
        Map<UUID, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<String> failed = new ArrayList<>();
        int succeeded = 0;

        for (UUID userId : request.getUserIds()) {
            try {
                User user = userMap.get(userId);
                if (user == null) {
                    failed.add(userId + ": user not found");
                    continue;
                }

                if (userId.equals(currentUserId)) {
                    failed.add(userId + ": cannot deactivate yourself");
                    continue;
                }

                if (user.getFirm() == null || !user.getFirm().getId().equals(firmId)) {
                    failed.add(userId + ": user does not belong to your firm");
                    continue;
                }

                if (!user.isActive()) {
                    failed.add(userId + ": user is already inactive");
                    continue;
                }

                user.setActive(false);
                userRepository.save(user);
                userRepository.incrementPermissionVersion(userId);
                permissionEvaluator.clearUserCache(userId);

                auditService.log(AuditAction.USER_DEACTIVATED, AuditEntity.USER, userId,
                        "Bulk deactivated: " + user.getUsername());

                succeeded++;
            } catch (Exception e) {
                failed.add(userId + ": " + e.getMessage());
            }
        }

        String message = succeeded + " of " + request.getUserIds().size() + " users deactivated";
        log.info("Bulk deactivate complete: {}", message);

        return BulkOperationResult.builder()
                .succeeded(succeeded)
                .failed(failed.size())
                .failedDetails(failed)
                .message(message)
                .build();
    }

    @Override
    @Transactional
    public BulkOperationResult bulkRoleChange(BulkRoleChangeRequest request) {
        UUID firmId = getRequiredFirmId();

        Role newRole = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        if (newRole.getFirm() == null || !newRole.getFirm().getId().equals(firmId)) {
            throw new ForbiddenException("Role does not belong to your firm");
        }
        if (Boolean.TRUE.equals(newRole.getIsSystem())) {
            throw new ForbiddenException("Cannot assign system roles directly");
        }
        if (!newRole.isActive()) {
            throw new BusinessRuleException("Cannot assign an inactive role");
        }
        if ("FIRM_ADMIN".equals(newRole.getRoleCode())) {
            throw new ForbiddenException("Cannot assign FIRM_ADMIN role. Only Super Admin can create Firm Admins.");
        }

        List<String> failed = new ArrayList<>();
        int succeeded = 0;

        // Batch-load all users in one query (avoids N+1)
        List<User> allUsers = userRepository.findAllById(request.getUserIds());
        Map<UUID, User> userMap = allUsers.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        for (UUID userId : request.getUserIds()) {
            try {
                User user = userMap.get(userId);
                if (user == null) {
                    failed.add(userId + ": user not found");
                    continue;
                }

                if (user.getFirm() == null || !user.getFirm().getId().equals(firmId)) {
                    failed.add(userId + ": user does not belong to your firm");
                    continue;
                }

                if (user.getUserType() == UserType.CLIENT && newRole.getApplicableTo() == UserType.FIRM_USER) {
                    failed.add(userId + ": cannot assign FIRM_USER role to a client");
                    continue;
                }
                if (user.getUserType() == UserType.FIRM_USER && newRole.getApplicableTo() == UserType.CLIENT) {
                    failed.add(userId + ": cannot assign CLIENT role to an employee");
                    continue;
                }

                String oldRole = user.getRole() != null ? user.getRole().getRoleCode() : "none";
                user.setRole(newRole);
                userRepository.save(user);
                userRepository.incrementPermissionVersion(userId);
                permissionEvaluator.clearUserCache(userId);

                auditService.log(AuditAction.USER_ROLE_CHANGED, AuditEntity.USER, userId,
                        "Bulk role change: " + oldRole + " → " + newRole.getRoleCode() +
                        " for user: " + user.getUsername());

                succeeded++;
            } catch (Exception e) {
                failed.add(userId + ": " + e.getMessage());
            }
        }

        String message = succeeded + " of " + request.getUserIds().size() +
                " users assigned role: " + newRole.getRoleCode();
        log.info("Bulk role change complete: {}", message);

        return BulkOperationResult.builder()
                .succeeded(succeeded)
                .failed(failed.size())
                .failedDetails(failed)
                .message(message)
                .build();
    }

    private UUID getRequiredFirmId() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }

    private User getValidatedUser(UUID userId, UUID firmId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getFirm() == null || !user.getFirm().getId().equals(firmId)) {
            throw new ForbiddenException("User does not belong to your firm");
        }
        return user;
    }
}
