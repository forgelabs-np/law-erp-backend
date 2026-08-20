package com.lawfirm.erp.firm.mapper;

import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.dto.admin.response.RoleResponse;
import com.lawfirm.erp.dto.firm.response.*;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.entity.FirmModule;
import com.lawfirm.erp.firm.entity.EmployeeProfile;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class FirmMapper {

    public ClientResponse toClientResponse(User user) {
        return ClientResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .mobileNo(user.getMobileNo())
                .fullName(user.getFullName())
                .userType(user.getUserType())
                .isActive(user.isActive())
                .portalAccessEnabled(user.getPortalAccessEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public EmployeeResponse toEmployeeResponse(User user, Role role, EmployeeProfile profile) {
        EmployeeResponse.EmployeeResponseBuilder builder = EmployeeResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .mobileNo(user.getMobileNo())
                .fullName(user.getFullName())
                .userType(user.getUserType())
                .isActive(user.isActive())
                .roleId(role != null ? role.getId() : null)
                .roleName(role != null ? role.getRoleName() : null)
                .roleCode(role != null ? role.getRoleCode() : null)
                .createdAt(user.getCreatedAt());

        if (profile != null) {
            builder.employeeCode(profile.getEmployeeCode())
                    .designation(profile.getDesignation())
                    .barCouncilNo(profile.getBarCouncilNo())
                    .specialization(profile.getSpecialization())
                    .joiningDate(profile.getJoiningDate())
                    .emergencyContactName(profile.getEmergencyContactName())
                    .emergencyContactPhone(profile.getEmergencyContactPhone())
                    .notes(profile.getNotes());
        }

        return builder.build();
    }

    public FirmAdminResponse toFirmAdminResponse(User user) {
        return FirmAdminResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .mobileNo(user.getMobileNo())
                .fullName(user.getFullName())
                .firmId(user.getFirmId())
                .firmName(user.getFirm() != null ? user.getFirm().getName() : null)
                .firmCode(user.getFirm() != null ? user.getFirm().getLawFirmCode() : null)
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public FirmProfileResponse toFirmProfileResponse(Firm firm) {
        return FirmProfileResponse.builder()
                .id(firm.getId())
                .lawFirmCode(firm.getLawFirmCode())
                .name(firm.getName())
                .firmType(firm.getFirmType())
                .status(firm.getStatus())
                .email(firm.getEmail())
                .phone(firm.getPhone())
                .address(firm.getAddress())
                .jurisdiction(firm.getJurisdiction())
                .logoUrl(firm.getLogoUrl())
                .createdAt(firm.getCreatedAt())
                .build();
    }

    public FirmModuleResponse toFirmModuleResponse(FirmModule fm) {
        return FirmModuleResponse.builder()
                .id(fm.getId())
                .moduleId(fm.getModule().getId())
                .moduleName(fm.getModule().getName())
                .moduleCode(fm.getModule().getCode())
                .isEnabled(fm.getIsEnabled())
                .enabledAt(fm.getEnabledAt())
                .expiresAt(fm.getExpiresAt())
                .isTrial(fm.getIsTrial())
                .maxFileSizeMb(fm.getMaxFileSizeMb())
                .allowedExtensions(fm.getAllowedExtensions())
                .notes(fm.getNotes())
                .build();
    }

    public RoleResponse toRoleResponse(Role role, int userCount, List<String> assignedUserNames) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getRoleName())
                .code(role.getRoleCode())
                .description(role.getDescription())
                .isSystem(role.getIsSystem())
                .isActive(role.isActive())
                .userCount(userCount)
                .assignedUserNames(assignedUserNames)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    public PermissionResponse toPermissionResponse(Permission p) {
        return PermissionResponse.builder()
                .id(p.getId())
                .action(p.getAction())
                .scope(p.getScope())
                .code(p.getCode())
                .description(p.getDescription())
                .isActive(p.isActive())
                .createdAt(p.getCreatedAt())
                .build();
    }

    public List<PermissionResponse> toPermissionResponseList(List<Permission> permissions) {
        return permissions.stream().map(this::toPermissionResponse).collect(Collectors.toList());
    }

    public RoleUserResponse toRoleUserResponse(User user) {
        return RoleUserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .email(user.getEmail())
                .mobileNo(user.getMobileNo())
                .isActive(user.isActive())
                .build();
    }
}
