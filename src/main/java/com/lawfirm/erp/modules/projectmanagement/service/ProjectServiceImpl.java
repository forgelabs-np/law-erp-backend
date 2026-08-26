package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.constant.ProjectManagementConstants;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.modules.projectmanagement.dto.request.*;
import com.lawfirm.erp.modules.projectmanagement.dto.response.*;
import com.lawfirm.erp.modules.projectmanagement.entity.*;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectMemberRole;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
import com.lawfirm.erp.modules.projectmanagement.mapper.ProjectMapper;
import com.lawfirm.erp.modules.projectmanagement.repository.*;
import com.lawfirm.erp.auth.security.FirmContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final CredentialRepository credentialRepository;
    private final RenewalRepository renewalRepository;
    private final RenewalInstanceRepository renewalInstanceRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ProjectMapper projectMapper;

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        UUID firmId = getRequiredFirmId();
        UUID currentUserId = currentUserResolver.getCurrentUserId();
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Resolve owner — defaults to creator
        UUID ownerId = request.getOwnerId() != null ? request.getOwnerId() : currentUserId;
        if (!ownerId.equals(currentUserId)) {
            validateUserBelongsToFirm(ownerId, firmId);
        }

        // Resolve client
        String clientName = request.getClientName();
        UUID clientUserId = request.getClientUserId();
        if (clientUserId != null) {
            User client = userRepository.findById(clientUserId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            ProjectManagementConstants.CLIENT_NOT_FOUND));
            if (client.getFirm() == null || !client.getFirm().getId().equals(firmId)) {
                throw new ForbiddenException("Client does not belong to this firm");
            }
            if (clientName == null || clientName.isBlank()) {
                clientName = client.getFullName();
            }
        }

        // Generate project code: FIRMCODE-PRJ-YYYY-NNNNN
        String firmCode = FirmContextHolder.getFirmCode();
        String projectCode = generateProjectCode(firmCode, firmId);

        Project project = Project.builder()
                .firmId(firmId)
                .clientUserId(clientUserId)
                .clientName(clientName)
                .projectCode(projectCode)
                .name(request.getName())
                .description(request.getDescription())
                .status(ProjectStatus.ACTIVE)
                .startDate(request.getStartDate())
                .targetEndDate(request.getTargetEndDate())
                .ownerId(ownerId)
                .build();
        project.setActive(true);
        project = projectRepository.save(project);

        // Auto-add owner as OWNER member
        ProjectMember ownerMember = ProjectMember.builder()
                .projectId(project.getId())
                .userId(ownerId)
                .roleInProject(ProjectMemberRole.OWNER)
                .build();
        memberRepository.save(ownerMember);

        auditService.log(AuditAction.PROJECT_CREATED, AuditEntity.PROJECT, project.getId(),
                "Project created: " + projectCode + " (" + project.getName() + ")");

        return projectMapper.toProjectResponse(project, List.of(ownerMember), 0, 0, 0);
    }

    public Page<ProjectSummaryResponse> listProjects(ProjectStatus status, String search,
                                                      int page, int size) {
        UUID firmId = getRequiredFirmId();
        UUID currentUserId = currentUserResolver.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Firm admins see all firm projects; employees only see projects they're members of
        boolean isAdmin = isFirmAdmin();
        Page<Project> projects;
        if (isAdmin) {
            if (status != null) {
                projects = projectRepository.findByFirmIdAndStatus(firmId, status, pageable);
            } else {
                projects = projectRepository.findByFirmId(firmId, pageable);
            }
        } else {
            if (status != null) {
                projects = projectRepository.findProjectsByMemberUserIdAndStatus(firmId, currentUserId, status, pageable);
            } else {
                projects = projectRepository.findProjectsByMemberUserId(firmId, currentUserId, pageable);
            }
        }

        return projects.map(p -> {
            int credCount = (int) credentialRepository.countByProjectIdAndActive(p.getId(), true);
            int renewCount = (int) renewalRepository.countByProjectIdAndActive(p.getId(), true);
            long overdueCount = renewalInstanceRepository.countOverdueByProjectId(p.getId());
            return projectMapper.toSummary(p, credCount, renewCount, overdueCount);
        });
    }

    public ProjectResponse getProject(String projectCode) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);
        List<ProjectMember> members = memberRepository.findByProjectId(project.getId());
        int credCount = (int) credentialRepository.countByProjectIdAndActive(project.getId(), true);
        int renewCount = (int) renewalRepository.countByProjectIdAndActive(project.getId(), true);
        long overdueCount = renewalInstanceRepository.countOverdueByProjectId(project.getId());
        return projectMapper.toProjectResponse(project, members, credCount, renewCount, overdueCount);
    }

    @Transactional
    public ProjectResponse updateProject(String projectCode, UpdateProjectRequest request) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);

        if (request.getName() != null) project.setName(request.getName());
        if (request.getClientName() != null) project.setClientName(request.getClientName());
        if (request.getDescription() != null) project.setDescription(request.getDescription());
        if (request.getStartDate() != null) project.setStartDate(request.getStartDate());
        if (request.getTargetEndDate() != null) project.setTargetEndDate(request.getTargetEndDate());
        if (request.getClientUserId() != null) project.setClientUserId(request.getClientUserId());
        if (request.getOwnerId() != null) project.setOwnerId(request.getOwnerId());

        project = projectRepository.save(project);
        return getProject(projectCode);
    }

    @Transactional
    public ProjectResponse updateProjectStatus(String projectCode, ProjectStatus status) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);
        project.setStatus(status);
        project = projectRepository.save(project);
        return getProject(projectCode);
    }

    @Transactional
    public ProjectMemberResponse addMember(String projectCode, AddMemberRequest request) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);

        validateUserBelongsToFirm(request.getUserId(), firmId);

        if (memberRepository.existsByProjectIdAndUserId(project.getId(), request.getUserId())) {
            throw new BusinessRuleException("User is already a member of this project");
        }

        ProjectMember member = ProjectMember.builder()
                .projectId(project.getId())
                .userId(request.getUserId())
                .roleInProject(request.getRole())
                .build();
        member = memberRepository.save(member);

        return projectMapper.toMemberResponse(member);
    }

    @Transactional
    public void removeMember(String projectCode, UUID userId) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);

        ProjectMember member = memberRepository.findByProjectIdAndUserId(project.getId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ProjectManagementConstants.MEMBER_NOT_FOUND));

        if (member.getRoleInProject() == ProjectMemberRole.OWNER) {
            throw new BusinessRuleException(ProjectManagementConstants.OWNER_CANNOT_BE_REMOVED);
        }

        memberRepository.deleteByProjectIdAndUserId(project.getId(), userId);
    }

    public List<ProjectMemberResponse> listMembers(String projectCode) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);
        return memberRepository.findByProjectId(project.getId()).stream()
                .map(projectMapper::toMemberResponse)
                .collect(Collectors.toList());
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private Project findProject(String projectCode, UUID firmId) {
        return projectRepository.findByProjectCodeAndFirmId(projectCode, firmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ProjectManagementConstants.PROJECT_NOT_FOUND));
    }

    private void validateUserBelongsToFirm(UUID userId, UUID firmId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getFirm() == null || !user.getFirm().getId().equals(firmId)) {
            throw new ForbiddenException("User does not belong to this firm");
        }
    }

    private String generateProjectCode(String firmCode, UUID firmId) {
        int nextSeq = 1;
        // Simple sequence — last created project's code parsed
        List<Project> recent = projectRepository.findByFirmId(firmId,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        if (!recent.isEmpty()) {
            String lastCode = recent.get(0).getProjectCode();
            try {
                nextSeq = Integer.parseInt(lastCode.substring(lastCode.lastIndexOf('-') + 1)) + 1;
            } catch (NumberFormatException ignored) {}
        }
        return String.format("%s-PRJ-%d-%05d", firmCode, Year.now().getValue(), nextSeq);
    }

    private UUID getRequiredFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }

    private boolean isFirmAdmin() {
        var user = currentUserResolver.getCurrentUser();
        return user != null && user.getRoles() != null && user.getRoles().contains("FIRM_ADMIN");
    }

    private final CurrentUserResolver currentUserResolver;
}
