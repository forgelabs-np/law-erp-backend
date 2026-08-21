package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.modules.projectmanagement.dto.response.ProjectDashboardResponse;
import com.lawfirm.erp.modules.projectmanagement.entity.Renewal;
import com.lawfirm.erp.modules.projectmanagement.entity.RenewalInstance;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus;
import com.lawfirm.erp.modules.projectmanagement.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

        // Count credentials across all projects
        List<UUID> allProjectIds = projectRepository.findByFirmId(firmId, PageRequest.of(0, 1000))
                .getContent().stream().map(p -> p.getId()).collect(Collectors.toList());
        long totalCredentials = allProjectIds.stream()
                .mapToLong(pid -> credentialRepository.countByProjectIdAndActive(pid, true))
                .sum();
        long totalRenewals = allProjectIds.stream()
                .mapToLong(pid -> renewalRepository.countByProjectIdAndActive(pid, true))
                .sum();

        // Overdue and upcoming
        List<ProjectDashboardResponse.OverdueItem> overdueItems = new ArrayList<>();
        List<ProjectDashboardResponse.UpcomingItem> upcomingItems = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate threeMonthsAhead = today.plusMonths(3);

        for (UUID projectId : allProjectIds) {
            List<Renewal> renewals = renewalRepository.findByProjectIdAndActive(projectId, true);
            for (Renewal renewal : renewals) {
                List<RenewalInstance> instances = instanceRepository
                        .findByRenewalIdAndActive(renewal.getId(), true);

                String typeName = renewalTypeRepository.findById(renewal.getRenewalTypeId())
                        .map(t -> t.getName()).orElse("Unknown");

                for (RenewalInstance inst : instances) {
                    if (inst.getStatus() == RenewalInstanceStatus.OVERDUE) {
                        int daysOverdue = (int) (LocalDate.now().toEpochDay() - inst.getDueDate().toEpochDay());
                        // Note: project name/code would need batch resolution for full implementation
                        overdueItems.add(ProjectDashboardResponse.OverdueItem.builder()
                                .renewalTitle(renewal.getTitle())
                                .renewalTypeName(typeName)
                                .dueDate(inst.getDueDate())
                                .daysOverdue(daysOverdue)
                                .build());
                    } else if (inst.getStatus() == RenewalInstanceStatus.PENDING
                            && !inst.getDueDate().isAfter(threeMonthsAhead)) {
                        int daysUntilDue = (int) (inst.getDueDate().toEpochDay() - LocalDate.now().toEpochDay());
                        upcomingItems.add(ProjectDashboardResponse.UpcomingItem.builder()
                                .renewalTitle(renewal.getTitle())
                                .renewalTypeName(typeName)
                                .dueDate(inst.getDueDate())
                                .daysUntilDue(daysUntilDue)
                                .build());
                    }
                }
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
