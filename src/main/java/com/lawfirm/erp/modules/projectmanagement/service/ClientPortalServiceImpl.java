package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.constant.ProjectManagementConstants;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
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
import java.util.List;
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

    public List<ClientProjectResponse> listMyProjects() {
        UUID clientId = currentUserResolver.getCurrentUserId();
        List<Project> projects = projectRepository.findByClientUserIdAndActive(clientId, true);

        return projects.stream()
                .map(p -> {
                    List<RenewalInstance> upcoming = getUpcomingInstances(p.getId());
                    return projectMapper.toClientProjectResponse(p, upcoming);
                })
                .collect(Collectors.toList());
    }

    public ClientProjectResponse getProject(String projectCode) {
        UUID clientId = currentUserResolver.getCurrentUserId();
        Project project = findByClientAndCode(clientId, projectCode);
        List<RenewalInstance> upcoming = getUpcomingInstances(project.getId());
        return projectMapper.toClientProjectResponse(project, upcoming);
    }

    public List<RenewalInstanceResponse> listMyRenewals(String projectCode) {
        UUID clientId = currentUserResolver.getCurrentUserId();
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

    // ─── Helpers ───────────────────────────────────────────────────────────

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
        // Find project by client user ID, then verify project code matches
        List<Project> projects = projectRepository.findByClientUserIdAndActive(clientId, true);
        return projects.stream()
                .filter(p -> p.getProjectCode().equals(projectCode))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        ProjectManagementConstants.PROJECT_NOT_FOUND));
    }
}
