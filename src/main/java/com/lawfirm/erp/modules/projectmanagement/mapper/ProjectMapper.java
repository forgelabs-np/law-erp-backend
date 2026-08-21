package com.lawfirm.erp.modules.projectmanagement.mapper;

import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.modules.projectmanagement.dto.response.*;
import com.lawfirm.erp.modules.projectmanagement.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProjectMapper {

    private final UserRepository userRepository;

    public ProjectResponse toProjectResponse(Project project, List<ProjectMember> members,
                                              int credentialCount, int renewalCount,
                                              long overdueInstances) {
        String ownerName = resolveUserName(project.getOwnerId());
        List<ProjectMemberResponse> memberResponses = members.stream()
                .map(this::toMemberResponse)
                .collect(Collectors.toList());

        return ProjectResponse.builder()
                .id(project.getId())
                .projectCode(project.getProjectCode())
                .name(project.getName())
                .clientName(project.getClientName())
                .clientUserId(project.getClientUserId())
                .description(project.getDescription())
                .status(project.getStatus())
                .startDate(project.getStartDate())
                .targetEndDate(project.getTargetEndDate())
                .ownerId(project.getOwnerId())
                .ownerName(ownerName)
                .members(memberResponses)
                .credentialCount(credentialCount)
                .renewalCount(renewalCount)
                .overdueInstances(overdueInstances)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    public ProjectSummaryResponse toSummary(Project project, int credentialCount,
                                             int renewalCount, long overdueInstances) {
        return ProjectSummaryResponse.builder()
                .id(project.getId())
                .projectCode(project.getProjectCode())
                .name(project.getName())
                .clientName(project.getClientName())
                .status(project.getStatus())
                .startDate(project.getStartDate())
                .targetEndDate(project.getTargetEndDate())
                .ownerName(resolveUserName(project.getOwnerId()))
                .credentialCount(credentialCount)
                .renewalCount(renewalCount)
                .overdueInstances(overdueInstances)
                .createdAt(project.getCreatedAt())
                .build();
    }

    public CredentialResponse toCredentialResponse(Credential credential) {
        return CredentialResponse.builder()
                .id(credential.getId())
                .siteName(credential.getSiteName())
                .siteType(credential.getSiteType())
                .siteUrl(credential.getSiteUrl())
                .usernameOrEmail(credential.getUsernameOrEmail())
                .password("••••••••")
                .contactPerson(credential.getContactPerson())
                .contactPhone(credential.getContactPhone())
                .contactEmail(credential.getContactEmail())
                .notes(credential.getNotes())
                .createdAt(credential.getCreatedAt())
                .build();
    }

    public RenewalResponse toRenewalResponse(Renewal renewal, String renewalTypeName,
                                              String assignedToName,
                                              List<RenewalInstance> instances) {
        List<RenewalInstanceResponse> instanceResponses = instances.stream()
                .map(this::toInstanceResponse)
                .collect(Collectors.toList());

        return RenewalResponse.builder()
                .id(renewal.getId())
                .renewalTypeId(renewal.getRenewalTypeId())
                .renewalTypeName(renewalTypeName)
                .title(renewal.getTitle())
                .description(renewal.getDescription())
                .recurrence(renewal.getRecurrence())
                .startDate(renewal.getStartDate())
                .endDate(renewal.getEndDate())
                .assignedToName(assignedToName)
                .status(renewal.getStatus())
                .instances(instanceResponses)
                .createdAt(renewal.getCreatedAt())
                .build();
    }

    public RenewalInstanceResponse toInstanceResponse(RenewalInstance instance) {
        return RenewalInstanceResponse.builder()
                .id(instance.getId())
                .dueDate(instance.getDueDate())
                .status(instance.getStatus())
                .completedAt(instance.getCompletedAt())
                .completedByName(instance.getCompletedById() != null
                        ? resolveUserName(instance.getCompletedById()) : null)
                .notes(instance.getNotes())
                .build();
    }

    public RenewalTypeResponse toRenewalTypeResponse(RenewalType type) {
        return RenewalTypeResponse.builder()
                .id(type.getId())
                .name(type.getName())
                .description(type.getDescription())
                .system(type.isSystem())
                .active(type.isActive())
                .build();
    }

    public ProjectMemberResponse toMemberResponse(ProjectMember member) {
        String userName = resolveUserName(member.getUserId());
        User user = userRepository.findById(member.getUserId()).orElse(null);
        return ProjectMemberResponse.builder()
                .id(member.getId())
                .userId(member.getUserId())
                .userName(userName)
                .userEmail(user != null ? user.getEmail() : null)
                .roleInProject(member.getRoleInProject())
                .addedAt(member.getCreatedAt())
                .build();
    }

    public ClientProjectResponse toClientProjectResponse(Project project,
                                                          List<RenewalInstance> upcomingInstances) {
        List<RenewalInstanceResponse> instanceResponses = upcomingInstances.stream()
                .map(this::toInstanceResponse)
                .collect(Collectors.toList());

        return ClientProjectResponse.builder()
                .id(project.getId())
                .projectCode(project.getProjectCode())
                .name(project.getName())
                .description(project.getDescription())
                .status(project.getStatus())
                .startDate(project.getStartDate())
                .targetEndDate(project.getTargetEndDate())
                .upcomingRenewals(instanceResponses)
                .createdAt(project.getCreatedAt())
                .build();
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getFullName).orElse(null);
    }
}
