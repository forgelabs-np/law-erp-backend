package com.lawfirm.erp.modules.usermanagement.dashboard;

import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.modules.audit.entity.AuditLog;
import com.lawfirm.erp.modules.audit.repository.AuditLogRepository;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.CourtEvent;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtEventRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.modules.invoice.entity.Invoice;
import com.lawfirm.erp.modules.invoice.enums.InvoiceStatus;
import com.lawfirm.erp.modules.invoice.repository.InvoiceRepository;
import com.lawfirm.erp.modules.projectmanagement.entity.Project;
import com.lawfirm.erp.modules.projectmanagement.entity.Renewal;
import com.lawfirm.erp.modules.projectmanagement.entity.RenewalInstance;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus;
import com.lawfirm.erp.modules.projectmanagement.repository.ProjectRepository;
import com.lawfirm.erp.modules.projectmanagement.repository.RenewalInstanceRepository;
import com.lawfirm.erp.modules.projectmanagement.repository.RenewalRepository;
import com.lawfirm.erp.modules.usermanagement.dto.response.FirmDashboardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirmDashboardServiceImpl implements FirmDashboardService {

    private final MatterRepository matterRepository;
    private final CourtCaseRepository courtCaseRepository;
    private final CourtEventRepository courtEventRepository;
    private final InvoiceRepository invoiceRepository;
    private final RenewalRepository renewalRepository;
    private final RenewalInstanceRepository renewalInstanceRepository;
    private final ProjectRepository projectRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    public FirmDashboardResponse getDashboard(DashboardScope scope) {
        UUID firmId = getRequiredFirmId(scope);

        List<UUID> leafIds = matterRepository.findLeafCourtCaseIdsByFirmId(firmId);

        return FirmDashboardResponse.builder()
                .caseStats(buildCaseStats(firmId, leafIds))
                .todayEvents(buildTodayEvents(firmId))
                .upcomingHearings(buildUpcomingHearings(firmId, leafIds))
                .invoiceStats(buildInvoiceStats(firmId))
                .overdueInvoices(buildOverdueInvoices(firmId))
                .renewalStats(buildRenewalStats(firmId))
                .upcomingRenewals(buildUpcomingRenewals(firmId))
                .teamCaseload(buildTeamCaseload(firmId, leafIds))
                .recentActivity(buildRecentActivity(firmId))
                .build();
    }

    private FirmDashboardResponse.CaseStats buildCaseStats(UUID firmId, List<UUID> leafIds) {
        long total = matterRepository.countByFirmId(firmId);
        long active = matterRepository.countByFirmIdAndStatus(firmId, MatterStatus.ACTIVE);
        long dormant = matterRepository.countByFirmIdAndStatus(firmId, MatterStatus.DORMANT);
        long closed = matterRepository.countByFirmIdAndStatus(firmId, MatterStatus.CLOSED);

        long stale = 0;
        if (!leafIds.isEmpty()) {
            LocalDate cutoff = LocalDate.now().minusDays(90);
            List<Object[]> latestPeshi = courtEventRepository.findLatestPeshiByCourtCaseIds(leafIds);
            Set<UUID> freshCases = latestPeshi.stream()
                    .filter(r -> ((LocalDate) r[1]).isAfter(cutoff))
                    .map(r -> (UUID) r[0])
                    .collect(Collectors.toSet());
            stale = leafIds.size() - freshCases.size();
        }

        return FirmDashboardResponse.CaseStats.builder()
                .totalMatters(total)
                .activeMatters(active)
                .dormantMatters(dormant)
                .closedMatters(closed)
                .staleMatters(stale)
                .build();
    }

    private List<FirmDashboardResponse.TodayEvent> buildTodayEvents(UUID firmId) {
        List<CourtEvent> events = courtEventRepository.findByFirmIdAndScheduledDate(firmId, LocalDate.now());
        if (events.isEmpty()) return List.of();

        Set<UUID> advocateIds = events.stream()
                .map(CourtEvent::getAttendingAdvocateId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> advocateNames = advocateIds.isEmpty() ? Map.of()
                : userRepository.findAllById(advocateIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        Set<UUID> ccIds = events.stream().map(CourtEvent::getCourtCaseId).collect(Collectors.toSet());
        Map<UUID, CourtCase> ccMap = ccIds.isEmpty() ? Map.of()
                : courtCaseRepository.findAllById(ccIds).stream()
                .collect(Collectors.toMap(CourtCase::getId, c -> c));
        Set<UUID> matterIds = ccMap.values().stream().map(CourtCase::getMatterId).collect(Collectors.toSet());
        Map<UUID, String> matterTitles = matterIds.isEmpty() ? Map.of()
                : matterRepository.findAllById(matterIds).stream()
                .collect(Collectors.toMap(m -> m.getId(), m -> m.getTitle()));

        return events.stream().map(e -> {
            CourtCase cc = ccMap.get(e.getCourtCaseId());
            UUID matterId = cc != null ? cc.getMatterId() : null;
            return FirmDashboardResponse.TodayEvent.builder()
                    .matterTitle(matterTitles.get(matterId))
                    .courtRoom(e.getCourtRoom())
                    .scheduledTime(e.getScheduledTime() != null ? e.getScheduledTime().toString() : null)
                    .judgeName(e.getJudgeName())
                    .attendingAdvocateName(e.getAttendingAdvocateId() != null
                            ? advocateNames.get(e.getAttendingAdvocateId()) : null)
                    .build();
        }).collect(Collectors.toList());
    }

    private List<FirmDashboardResponse.UpcomingHearing> buildUpcomingHearings(UUID firmId, List<UUID> leafIds) {
        if (leafIds.isEmpty()) return List.of();

        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(14);

        Map<UUID, String> courtNames = courtCaseRepository.findAllById(leafIds).stream()
                .collect(Collectors.toMap(CourtCase::getId, CourtCase::getCourtName));

        List<CourtEvent> allEvents = courtEventRepository
                .findByCourtCaseIdInAndFirmIdOrderBySequenceNoAsc(leafIds, firmId);

        Set<UUID> matterIds = courtCaseRepository.findAllById(leafIds).stream()
                .map(CourtCase::getMatterId).collect(Collectors.toSet());
        Map<UUID, String> matterTitles = matterIds.isEmpty() ? Map.of()
                : matterRepository.findAllById(matterIds).stream()
                .collect(Collectors.toMap(m -> m.getId(), m -> m.getTitle()));

        Map<UUID, String> ccToMatterTitle = courtCaseRepository.findAllById(leafIds).stream()
                .collect(Collectors.toMap(CourtCase::getId, cc -> matterTitles.getOrDefault(cc.getMatterId(), "")));

        return allEvents.stream()
                .filter(e -> e.getStatus() == CourtEventStatus.SCHEDULED)
                .filter(e -> e.getScheduledDate() != null
                        && !e.getScheduledDate().isBefore(today)
                        && !e.getScheduledDate().isAfter(cutoff))
                .map(e -> {
                    int daysUntil = (int) ChronoUnit.DAYS.between(today, e.getScheduledDate());
                    return FirmDashboardResponse.UpcomingHearing.builder()
                            .matterTitle(ccToMatterTitle.get(e.getCourtCaseId()))
                            .hearingDate(e.getScheduledDate())
                            .courtName(courtNames.getOrDefault(e.getCourtCaseId(), ""))
                            .daysUntil(daysUntil)
                            .build();
                })
                .sorted(Comparator.comparing(FirmDashboardResponse.UpcomingHearing::getHearingDate))
                .limit(10)
                .collect(Collectors.toList());
    }

    private FirmDashboardResponse.InvoiceStats buildInvoiceStats(UUID firmId) {
        long total = invoiceRepository.countByFirmId(firmId);
        long overdue = invoiceRepository.findByFirmIdAndStatus(firmId, InvoiceStatus.OVERDUE,
                PageRequest.of(0, 1)).getTotalElements();
        long sent = invoiceRepository.findByFirmIdAndStatus(firmId, InvoiceStatus.SENT,
                PageRequest.of(0, 1)).getTotalElements();

        return FirmDashboardResponse.InvoiceStats.builder()
                .totalInvoices(total)
                .outstanding(sent + overdue)
                .overdue(overdue)
                .totalOutstandingAmount(BigDecimal.ZERO)
                .overdueAmount(BigDecimal.ZERO)
                .build();
    }

    private List<FirmDashboardResponse.OverdueInvoice> buildOverdueInvoices(UUID firmId) {
        List<Invoice> overdue = invoiceRepository.findByFirmIdAndStatus(firmId, InvoiceStatus.OVERDUE,
                PageRequest.of(0, 5)).getContent();
        LocalDate today = LocalDate.now();

        return overdue.stream().map(inv -> {
            int daysOverdue = inv.getDueDate() != null
                    ? (int) ChronoUnit.DAYS.between(inv.getDueDate(), today) : 0;
            return FirmDashboardResponse.OverdueInvoice.builder()
                    .invoiceNumber(inv.getInvoiceNumber())
                    .clientName(null)
                    .amount(inv.getTotal())
                    .daysOverdue(daysOverdue)
                    .build();
        }).collect(Collectors.toList());
    }

    private FirmDashboardResponse.RenewalStats buildRenewalStats(UUID firmId) {
        LocalDate today = LocalDate.now();
        LocalDate monthEnd = today.plusMonths(1);

        List<RenewalInstance> allInstances = getAllRenewalInstancesForFirm(firmId);
        long dueThisMonth = allInstances.stream()
                .filter(i -> i.getStatus() == RenewalInstanceStatus.PENDING
                        && i.getDueDate() != null
                        && !i.getDueDate().isBefore(today)
                        && !i.getDueDate().isAfter(monthEnd))
                .count();
        long overdue = allInstances.stream()
                .filter(i -> i.getStatus() == RenewalInstanceStatus.OVERDUE)
                .count();

        return FirmDashboardResponse.RenewalStats.builder()
                .dueThisMonth(dueThisMonth)
                .overdue(overdue)
                .build();
    }

    private List<FirmDashboardResponse.UpcomingRenewal> buildUpcomingRenewals(UUID firmId) {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusMonths(3);

        List<RenewalInstance> allInstances = getAllRenewalInstancesForFirm(firmId);

        Set<Long> renewalIds = allInstances.stream().map(RenewalInstance::getRenewalId).collect(Collectors.toSet());
        Map<Long, Renewal> renewalMap = renewalIds.isEmpty() ? Map.of()
                : renewalRepository.findAllById(renewalIds).stream()
                .collect(Collectors.toMap(Renewal::getId, r -> r));

        Set<UUID> projectIds = renewalMap.values().stream().map(Renewal::getProjectId).collect(Collectors.toSet());
        Map<UUID, String> projectNames = projectIds.isEmpty() ? Map.of()
                : projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));

        return allInstances.stream()
                .filter(i -> i.getStatus() == RenewalInstanceStatus.PENDING
                        && i.getDueDate() != null
                        && !i.getDueDate().isBefore(today)
                        && !i.getDueDate().isAfter(cutoff))
                .map(i -> {
                    Renewal renewal = renewalMap.get(i.getRenewalId());
                    String projectName = renewal != null ? projectNames.get(renewal.getProjectId()) : null;
                    int daysUntil = (int) ChronoUnit.DAYS.between(today, i.getDueDate());
                    return FirmDashboardResponse.UpcomingRenewal.builder()
                            .projectName(projectName)
                            .renewalTitle(renewal != null ? renewal.getTitle() : null)
                            .dueDate(i.getDueDate())
                            .daysUntil(daysUntil)
                            .build();
                })
                .sorted(Comparator.comparing(FirmDashboardResponse.UpcomingRenewal::getDueDate))
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<RenewalInstance> getAllRenewalInstancesForFirm(UUID firmId) {
        List<UUID> projectIds = projectRepository.findByFirmId(firmId, PageRequest.of(0, 1000)).getContent()
                .stream().map(Project::getId).collect(Collectors.toList());
        if (projectIds.isEmpty()) return List.of();

        List<Renewal> renewals = renewalRepository.findByProjectIdInAndActive(projectIds, true);
        if (renewals.isEmpty()) return List.of();

        List<Long> renewalIds = renewals.stream().map(Renewal::getId).collect(Collectors.toList());
        return renewalInstanceRepository.findByRenewalIdInAndActive(renewalIds, true);
    }

    private List<FirmDashboardResponse.TeamCaseload> buildTeamCaseload(UUID firmId, List<UUID> leafIds) {
        List<User> advocates = userRepository.findByFirmIdAndUserType(firmId, UserType.FIRM_USER);

        Map<UUID, Long> caseloadMap = new LinkedHashMap<>();
        if (!leafIds.isEmpty()) {
            List<CourtCase> cases = courtCaseRepository.findAllById(leafIds);
            caseloadMap = cases.stream()
                    .filter(cc -> cc.getAdvocateId() != null)
                    .collect(Collectors.groupingBy(CourtCase::getAdvocateId, Collectors.counting()));
        }

        Map<UUID, Long> finalCaseload = caseloadMap;
        return advocates.stream()
                .map(u -> FirmDashboardResponse.TeamCaseload.builder()
                        .advocateName(u.getFullName())
                        .openMatters(finalCaseload.getOrDefault(u.getId(), 0L))
                        .build())
                .filter(tc -> tc.getOpenMatters() > 0)
                .sorted(Comparator.comparingLong(FirmDashboardResponse.TeamCaseload::getOpenMatters).reversed())
                .collect(Collectors.toList());
    }

    private List<FirmDashboardResponse.RecentActivity> buildRecentActivity(UUID firmId) {
        List<AuditLog> logs = auditLogRepository.findRecentByFirm(firmId, PageRequest.of(0, 10)).getContent();
        if (logs.isEmpty()) return List.of();

        List<UUID> userIds = logs.stream().map(AuditLog::getUserId).distinct().collect(Collectors.toList());
        Map<UUID, String> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return logs.stream().map(log -> FirmDashboardResponse.RecentActivity.builder()
                .summary(log.getSummary())
                .userName(userMap.get(log.getUserId()))
                .createdAt(log.getCreatedAt())
                .build()).collect(Collectors.toList());
    }

    private UUID getRequiredFirmId(DashboardScope scope) {
        if (scope.getFirmId() == null) throw new ForbiddenException("Firm context required");
        return scope.getFirmId();
    }
}
