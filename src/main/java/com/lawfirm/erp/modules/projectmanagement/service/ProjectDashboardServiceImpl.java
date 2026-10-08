package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.modules.projectmanagement.dto.response.ProjectDashboardResponse;
import com.lawfirm.erp.modules.projectmanagement.entity.Project;
import com.lawfirm.erp.modules.projectmanagement.entity.Renewal;
import com.lawfirm.erp.modules.projectmanagement.entity.RenewalInstance;
import com.lawfirm.erp.modules.projectmanagement.entity.RenewalType;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus;
import com.lawfirm.erp.modules.projectmanagement.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectDashboardServiceImpl implements ProjectDashboardService {

    private final ProjectRepository projectRepository;
    private final CredentialRepository credentialRepository;
    private final RenewalRepository renewalRepository;
    private final RenewalInstanceRepository instanceRepository;
    private final RenewalTypeRepository renewalTypeRepository;

    public ProjectDashboardResponse getDashboard() {
        UUID firmId = getRequiredFirmId();

        long totalProjects = projectRepository.findByFirmId(firmId, PageRequest.of(0, 1)).getTotalElements();
        long activeProjects = projectRepository.countByFirmIdAndStatus(firmId, ProjectStatus.ACTIVE);
        long onHoldProjects = projectRepository.countByFirmIdAndStatus(firmId, ProjectStatus.ON_HOLD);
        long completedProjects = projectRepository.countByFirmIdAndStatus(firmId, ProjectStatus.COMPLETED);

        List<Project> projects = projectRepository.findByFirmId(firmId, PageRequest.of(0, 1000)).getContent();
        List<UUID> allProjectIds = projects.stream().map(Project::getId).collect(Collectors.toList());

        long totalCredentials = allProjectIds.isEmpty() ? 0L
                : credentialRepository.countByProjectIdInAndActive(allProjectIds, true);
        long totalRenewals = allProjectIds.isEmpty() ? 0L
                : renewalRepository.countByProjectIdInAndActive(allProjectIds, true);

        Map<UUID, Project> projectMap = projects.stream()
                .collect(Collectors.toMap(Project::getId, p -> p));

        // Batched: one renewals query, one instances query and one types lookup for the whole firm
        // instead of a nested per-project / per-renewal loop (which was N+1 across three levels).
        List<Renewal> renewals = allProjectIds.isEmpty() ? List.of()
                : renewalRepository.findByProjectIdInAndActive(allProjectIds, true);
        Map<Long, Renewal> renewalById = renewals.stream()
                .collect(Collectors.toMap(Renewal::getId, r -> r));

        List<RenewalInstance> instances = renewalById.isEmpty() ? List.of()
                : instanceRepository.findByRenewalIdInAndActive(new ArrayList<>(renewalById.keySet()), true);

        Map<Long, String> typeNames = renewals.isEmpty() ? Map.of()
                : renewalTypeRepository.findAllById(
                        renewals.stream().map(Renewal::getRenewalTypeId).distinct().collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(RenewalType::getId, RenewalType::getName));

        List<ProjectDashboardResponse.OverdueItem> overdueItems = new ArrayList<>();
        List<ProjectDashboardResponse.UpcomingItem> upcomingItems = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate threeMonthsAhead = today.plusMonths(3);

        for (RenewalInstance inst : instances) {
            Renewal renewal = renewalById.get(inst.getRenewalId());
            if (renewal == null) continue;

            Project project = projectMap.get(renewal.getProjectId());
            String projectCode = project != null ? project.getProjectCode() : null;
            String projectName = project != null ? project.getName() : null;
            String typeName = typeNames.getOrDefault(renewal.getRenewalTypeId(), "Unknown");

            if (inst.getStatus() == RenewalInstanceStatus.OVERDUE) {
                int daysOverdue = (int) (today.toEpochDay() - inst.getDueDate().toEpochDay());
                overdueItems.add(ProjectDashboardResponse.OverdueItem.builder()
                        .projectCode(projectCode)
                        .projectName(projectName)
                        .renewalTitle(renewal.getTitle())
                        .renewalTypeName(typeName)
                        .dueDate(inst.getDueDate())
                        .daysOverdue(daysOverdue)
                        .build());
            } else if (inst.getStatus() == RenewalInstanceStatus.PENDING
                    && !inst.getDueDate().isAfter(threeMonthsAhead)) {
                int daysUntilDue = (int) (inst.getDueDate().toEpochDay() - today.toEpochDay());
                upcomingItems.add(ProjectDashboardResponse.UpcomingItem.builder()
                        .projectCode(projectCode)
                        .projectName(projectName)
                        .renewalTitle(renewal.getTitle())
                        .renewalTypeName(typeName)
                        .dueDate(inst.getDueDate())
                        .daysUntilDue(daysUntilDue)
                        .build());
            }
        }

        overdueItems.sort((a, b) -> b.getDaysOverdue() - a.getDaysOverdue());
        upcomingItems.sort((a, b) -> a.getDaysUntilDue() - b.getDaysUntilDue());

        return ProjectDashboardResponse.builder()
                .totalProjects(totalProjects)
                .activeProjects(activeProjects)
                .onHoldProjects(onHoldProjects)
                .completedProjects(completedProjects)
                .totalCredentials(totalCredentials)
                .totalRenewals(totalRenewals)
                .overdueInstances(overdueItems.size())
                .upcomingInstances(upcomingItems.size())
                .overdueItems(overdueItems)
                .upcomingItems(upcomingItems)
                .build();
    }

    private UUID getRequiredFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }
}
