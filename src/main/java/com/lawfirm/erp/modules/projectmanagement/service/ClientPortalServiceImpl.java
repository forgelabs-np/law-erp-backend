package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.constant.ProjectManagementConstants;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.modules.projectmanagement.dto.response.ClientProjectResponse;
import com.lawfirm.erp.modules.projectmanagement.dto.response.RenewalInstanceResponse;
import com.lawfirm.erp.modules.projectmanagement.entity.Project;
import com.lawfirm.erp.modules.projectmanagement.entity.Renewal;
import com.lawfirm.erp.modules.projectmanagement.entity.RenewalInstance;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus;
import com.lawfirm.erp.modules.projectmanagement.mapper.ProjectMapper;
import com.lawfirm.erp.modules.projectmanagement.repository.ProjectRepository;
import com.lawfirm.erp.modules.projectmanagement.repository.RenewalInstanceRepository;
import com.lawfirm.erp.modules.projectmanagement.repository.RenewalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientPortalServiceImpl implements ClientPortalService {

    private final ProjectRepository projectRepository;
    private final RenewalRepository renewalRepository;
    private final RenewalInstanceRepository instanceRepository;
    private final CurrentUserResolver currentUserResolver;
    private final ProjectMapper projectMapper;
    private final UserRepository userRepository;

    private UUID requirePortalAccess() {
        UUID clientId = currentUserResolver.getCurrentUserId();
        User user = clientId == null ? null : userRepository.findById(clientId).orElse(null);
        if (user == null || user.getUserType() != UserType.CLIENT
                || !Boolean.TRUE.equals(user.getPortalAccessEnabled())) {
            throw new ForbiddenException(
                    "Client portal access is disabled for your account. Please contact your firm.");
        }
        return clientId;
    }

    public List<ClientProjectResponse> listMyProjects() {
        UUID clientId = requirePortalAccess();
        List<Project> projects = projectRepository.findByClientUserIdAndActive(clientId, true);

        // One renewals query + one instances query for all the client's projects, instead of
        // 2 queries per project (and one more per renewal) as the previous loop did.
        Map<UUID, List<RenewalInstance>> upcomingByProject = upcomingInstancesByProject(
                projects.stream().map(Project::getId).collect(Collectors.toList()));

        return projects.stream()
                .map(p -> projectMapper.toClientProjectResponse(
                        p, upcomingByProject.getOrDefault(p.getId(), List.of())))
                .collect(Collectors.toList());
    }

    /** Projects the client's renewals onto their due instances in three queries total. */
    private Map<UUID, List<RenewalInstance>> upcomingInstancesByProject(List<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        List<Renewal> renewals = renewalRepository.findByProjectIdInAndActive(projectIds, true);
        if (renewals.isEmpty()) {
            return Map.of();
        }
        Map<Long, UUID> projectByRenewal = renewals.stream()
                .collect(Collectors.toMap(Renewal::getId, Renewal::getProjectId));

        List<RenewalInstance> instances = instanceRepository
                .findByRenewalIdInAndActive(new ArrayList<>(projectByRenewal.keySet()), true);

        LocalDate sixMonthsAhead = LocalDate.now().plusMonths(6);
        Map<UUID, List<RenewalInstance>> byProject = new HashMap<>();
        for (RenewalInstance instance : instances) {
            if (instance.getStatus() != RenewalInstanceStatus.PENDING
                    && instance.getStatus() != RenewalInstanceStatus.OVERDUE) {
                continue;
            }
            if (instance.getDueDate().isAfter(sixMonthsAhead)) {
                continue;
            }
            UUID projectId = projectByRenewal.get(instance.getRenewalId());
            if (projectId != null) {
                byProject.computeIfAbsent(projectId, k -> new ArrayList<>()).add(instance);
            }
        }
        byProject.values().forEach(list -> list.sort(Comparator.comparing(RenewalInstance::getDueDate)));
        return byProject;
    }

    public ClientProjectResponse getProject(String projectCode) {
        UUID clientId = requirePortalAccess();
        Project project = findByClientAndCode(clientId, projectCode);
        List<RenewalInstance> upcoming = getUpcomingInstances(project.getId());
        return projectMapper.toClientProjectResponse(project, upcoming);
    }

    public List<RenewalInstanceResponse> listMyRenewals(String projectCode) {
        UUID clientId = requirePortalAccess();
        Project project = findByClientAndCode(clientId, projectCode);

        List<Renewal> renewals = renewalRepository.findByProjectIdAndActive(project.getId(), true);
        return renewals.stream()
                .flatMap(r -> instanceRepository.findByRenewalIdAndActive(r.getId(), true).stream())
                .filter(i -> i.getStatus() == RenewalInstanceStatus.PENDING
                        || i.getStatus() == RenewalInstanceStatus.OVERDUE
                        || i.getStatus() == RenewalInstanceStatus.IN_PROGRESS)
                .sorted((a, b) -> a.getDueDate().compareTo(b.getDueDate()))
                .map(projectMapper::toInstanceResponse)
                .collect(Collectors.toList());
    }


    private List<RenewalInstance> getUpcomingInstances(java.util.UUID projectId) {
        List<Renewal> renewals = renewalRepository.findByProjectIdAndActive(projectId, true);
        LocalDate sixMonthsAhead = LocalDate.now().plusMonths(6);
        return renewals.stream()
                .flatMap(r -> instanceRepository.findByRenewalIdAndActive(r.getId(), true).stream())
                .filter(i -> i.getStatus() == RenewalInstanceStatus.PENDING
                        || i.getStatus() == RenewalInstanceStatus.OVERDUE)
                .filter(i -> !i.getDueDate().isAfter(sixMonthsAhead))
                .sorted((a, b) -> a.getDueDate().compareTo(b.getDueDate()))
                .collect(Collectors.toList());
    }

    private Project findByClientAndCode(UUID clientId, String projectCode) {
        List<Project> projects = projectRepository.findByClientUserIdAndActive(clientId, true);
        return projects.stream()
                .filter(p -> p.getProjectCode().equals(projectCode))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        ProjectManagementConstants.PROJECT_NOT_FOUND));
    }
}
