package com.lawfirm.erp.modules.usermanagement.dashboard;

import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.entity.AuditLog;
import com.lawfirm.erp.modules.audit.repository.AuditLogRepository;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.modules.scraper.repository.CourtRepository;
import com.lawfirm.erp.modules.scraper.repository.DailyHearingRepository;
import com.lawfirm.erp.modules.scraper.repository.HearingMatchRepository;
import com.lawfirm.erp.modules.usermanagement.dto.response.SuperAdminDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SuperAdminDashboardServiceImpl implements SuperAdminDashboardService {

    private final FirmRepository firmRepository;
    private final UserRepository userRepository;
    private final MatterRepository matterRepository;
    private final AuditLogRepository auditLogRepository;
    private final CourtRepository courtRepository;
    private final DailyHearingRepository dailyHearingRepository;
    private final HearingMatchRepository hearingMatchRepository;

    @Override
    public SuperAdminDashboardResponse getDashboard(DashboardScope scope) {
        return SuperAdminDashboardResponse.builder()
                .firmStats(buildFirmStats())
                .userStats(buildUserStats())
                .caseStats(buildCaseStats())
                .scraperStats(buildScraperStats())
                .trialAlerts(buildTrialAlerts())
                .recentActivity(buildRecentActivity())
                .matterTrends(buildMatterTrends(scope.getNow()))
                .build();
    }

    /** 3 COUNT queries, zero rows loaded. */
    private SuperAdminDashboardResponse.FirmStats buildFirmStats() {
        long total = firmRepository.countFirmsWithFirmAdmin();
        long active = firmRepository.countActiveFirmsWithFirmAdmin();
        long trial = firmRepository.countByIsTrialTrue();

        return SuperAdminDashboardResponse.FirmStats.builder()
                .totalFirms(total)
                .activeFirms(active)
                .suspendedFirms(total - active)
                .trialFirms(trial)
                .build();
    }

    /** 5 COUNT queries, zero rows loaded. */
    private SuperAdminDashboardResponse.UserStats buildUserStats() {
        long total = userRepository.count();
        long superAdmins = userRepository.countByUserType(UserType.SUPER_ADMIN);
        long firmAdmins = userRepository.countByUserType(UserType.FIRM);
        long firmUsers = userRepository.countByUserType(UserType.FIRM_USER);
        long clients = userRepository.countByUserType(UserType.CLIENT);

        Map<String, Long> byRole = new LinkedHashMap<>();
        byRole.put("SUPER_ADMIN", superAdmins);
        byRole.put("FIRM_ADMIN", firmAdmins);
        byRole.put("FIRM_USER", firmUsers);
        byRole.put("CLIENT", clients);

        return SuperAdminDashboardResponse.UserStats.builder()
                .totalUsers(total)
                .activeUsers(superAdmins + firmAdmins + firmUsers)
                .byRole(byRole)
                .build();
    }

    /** 3 COUNT queries via repository, zero rows loaded. */
    private SuperAdminDashboardResponse.CaseStats buildCaseStats() {
        long total = matterRepository.count();
        long active = matterRepository.countByStatus(MatterStatus.ACTIVE);
        long closed = matterRepository.countByStatus(MatterStatus.CLOSED);

        return SuperAdminDashboardResponse.CaseStats.builder()
                .totalMatters(total)
                .activeMatters(active)
                .closedMatters(closed)
                .build();
    }

    /** 3 COUNT queries + 1 scalar, zero rows loaded. */
    private SuperAdminDashboardResponse.ScraperStats buildScraperStats() {
        return SuperAdminDashboardResponse.ScraperStats.builder()
                .courtsTracked(courtRepository.count())
                .totalHearings(dailyHearingRepository.count())
                .totalMatches(hearingMatchRepository.count())
                .lastScrapeTime(dailyHearingRepository.findMaxScrapedDate()
                        .map(d -> d.atStartOfDay()).orElse(null))
                .build();
    }

    /** 2 COUNT queries with WHERE filters, zero rows loaded. */
    private SuperAdminDashboardResponse.TrialAlerts buildTrialAlerts() {
        LocalDateTime now = LocalDateTime.now();
        long expiring = firmRepository.countTrialExpiringBetween(now, now.plusDays(7));
        long expired = firmRepository.countTrialExpiredBefore(now);

        return SuperAdminDashboardResponse.TrialAlerts.builder()
                .expiringThisWeek(expiring)
                .expired(expired)
                .build();
    }

    /** 1 query for logs + 1 batch user lookup. */
    private List<SuperAdminDashboardResponse.RecentActivity> buildRecentActivity() {
        List<AuditLog> logs = auditLogRepository.findRecent(PageRequest.of(0, 10)).getContent();
        if (logs.isEmpty()) return List.of();

        List<UUID> userIds = logs.stream().map(AuditLog::getUserId).distinct()
                .collect(Collectors.toList());
        Map<UUID, String> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return logs.stream().map(log -> SuperAdminDashboardResponse.RecentActivity.builder()
                .summary(log.getSummary())
                .action(log.getAction() != null ? log.getAction().name() : null)
                .userName(userMap.get(log.getUserId()))
                .createdAt(log.getCreatedAt())
                .build()).collect(Collectors.toList());
    }

    /** 1 GROUP BY query for daily counts. */
    private List<SuperAdminDashboardResponse.MatterTrend> buildMatterTrends(LocalDateTime now) {
        int days = 30;
        LocalDateTime from = now.toLocalDate().minusDays(days).atStartOfDay();
        LocalDateTime to = now.toLocalDate().plusDays(1).atStartOfDay();

        List<Object[]> rows = matterRepository.countDailyByDateRange(from, to);
        Map<LocalDate, long[]> dailyMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            dailyMap.put(date, new long[]{
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(),
                    ((Number) row[3]).longValue()
            });
        }

        List<SuperAdminDashboardResponse.MatterTrend> trends = new java.util.ArrayList<>();
        long cumTotal = 0, cumActive = 0, cumClosed = 0;
        LocalDate end = now.toLocalDate();
        LocalDate start = end.minusDays(days);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            long[] day = dailyMap.getOrDefault(d, new long[3]);
            cumTotal += day[0];
            cumActive += day[1];
            cumClosed += day[2];
            trends.add(SuperAdminDashboardResponse.MatterTrend.builder()
                    .date(d).total(cumTotal).active(cumActive).closed(cumClosed)
                    .build());
        }
        return trends;
    }
}
