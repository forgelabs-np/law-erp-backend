package com.lawfirm.erp.modules.usermanagement.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.entity.AuditLog;
import com.lawfirm.erp.modules.audit.repository.AuditLogRepository;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.repository.CourtEventRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.modules.scraper.repository.CourtRepository;
import com.lawfirm.erp.modules.scraper.repository.DailyHearingRepository;
import com.lawfirm.erp.modules.scraper.repository.HearingMatchRepository;
import com.lawfirm.erp.modules.scraper.repository.WeeklyHearingRepository;
import com.lawfirm.erp.modules.usermanagement.dto.response.GlobalDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GlobalDashboardServiceImpl implements GlobalDashboardService {

    private final UserRepository userRepository;
    private final FirmRepository firmRepository;
    private final MatterRepository matterRepository;
    private final CourtEventRepository courtEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final CourtRepository courtRepository;
    private final DailyHearingRepository dailyHearingRepository;
    private final WeeklyHearingRepository weeklyHearingRepository;
    private final HearingMatchRepository hearingMatchRepository;
    private final CurrentUserResolver currentUserResolver;

    @Override
    public GlobalDashboardResponse getDashboard() {
        return getDashboard(30); // default 30 days
    }

    @Override
    public GlobalDashboardResponse getDashboard(int days) {
        boolean superAdmin = currentUserResolver.isSuperAdmin();
        UUID firmId = superAdmin ? null : getRequiredFirmId();

        // Clamp days to reasonable range
        int effectiveDays = Math.max(1, Math.min(days, 365));

        return GlobalDashboardResponse.builder()
                .userStats(buildUserStats(firmId))
                .firmStats(buildFirmStats(firmId))
                .caseStats(buildCaseStats(firmId))
                .scraperStats(buildScraperStats())
                .recentActivity(buildRecentActivity(firmId))
                .userTrends(buildUserTrends(firmId, effectiveDays))
                .matterTrends(buildMatterTrends(firmId, effectiveDays))
                .firmTrends(buildFirmTrends(effectiveDays))
                .build();
    }

    private GlobalDashboardResponse.UserStats buildUserStats(UUID firmId) {
        // Pure COUNT queries — zero rows loaded into memory
        long total, active;
        if (firmId != null) {
            total = userRepository.countByFirmId(firmId);
            active = userRepository.countActiveByFirmId(firmId);
        } else {
            total = userRepository.count();
            active = userRepository.countByUserType(UserType.FIRM)
                    + userRepository.countByUserType(UserType.FIRM_USER)
                    + userRepository.countByUserType(UserType.SUPER_ADMIN);
        }
        long inactive = total - active;

        long advocates = firmId != null
                ? userRepository.countByFirmIdAndRoleCode(firmId, "ADVOCATE")
                : userRepository.countByUserType(UserType.FIRM_USER)
                        + userRepository.countByUserType(UserType.FIRM); // rough upper bound
        long paralegals = firmId != null
                ? userRepository.countByFirmIdAndRoleCode(firmId, "PARALEGAL")
                : 0;
        long clients = firmId != null
                ? userRepository.findByFirmIdAndUserType(firmId, UserType.CLIENT).size()
                : userRepository.countByUserType(UserType.CLIENT);
        long firmAdmins = firmId != null
                ? userRepository.countByFirmIdAndRoleCode(firmId, "FIRM_ADMIN")
                : 0;

        return GlobalDashboardResponse.UserStats.builder()
                .totalUsers(total)
                .activeUsers(active)
                .inactiveUsers(inactive)
                .totalAdvocates(advocates)
                .totalParalegals(paralegals)
                .totalClients(clients)
                .totalFirmAdmins(firmAdmins)
                .build();
    }

    private GlobalDashboardResponse.FirmStats buildFirmStats(UUID firmId) {
        long total, active;
        if (firmId != null) {
            total = 1;
            active = firmRepository.findById(firmId)
                    .map(f -> f.getStatus() == FirmStatus.ACTIVE ? 1L : 0L)
                    .orElse(0L);
        } else {
            // Only count firms that have at least one FIRM_ADMIN user
            total = firmRepository.countFirmsWithFirmAdmin();
            active = firmRepository.countActiveFirmsWithFirmAdmin();
        }
        return GlobalDashboardResponse.FirmStats.builder()
                .totalFirms(total)
                .activeFirms(active)
                .suspendedFirms(total - active)
                .build();
    }

    private GlobalDashboardResponse.CaseStats buildCaseStats(UUID firmId) {
        // Pure COUNT queries — no full-table scan
        long total = firmId != null
                ? matterRepository.findByFirmId(firmId, org.springframework.data.domain.Pageable.unpaged()).getTotalElements()
                : matterRepository.count();
        long active = firmId != null
                ? matterRepository.countByFirmIdAndStatus(firmId, MatterStatus.ACTIVE)
                : 0;
        long closed = firmId != null
                ? matterRepository.countByFirmIdAndStatus(firmId, MatterStatus.CLOSED)
                : 0;

        // Stale matters: only load leaf IDs where currentCourtCaseId is set, limited to firm
        List<Matter> matters = firmId != null
                ? matterRepository.findByFirmId(firmId, org.springframework.data.domain.Pageable.unpaged()).getContent()
                : matterRepository.findAll();
        List<UUID> leafIds = matters.stream()
                .map(Matter::getCurrentCourtCaseId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        long stale = 0;
        if (!leafIds.isEmpty()) {
            List<Object[]> latestPeshi = courtEventRepository.findLatestPeshiByCourtCaseIds(leafIds);
            var peshiMap = latestPeshi.stream()
                    .collect(Collectors.toMap(r -> (UUID) r[0], r -> (LocalDate) r[1]));
            LocalDate cutoff = LocalDate.now().minusDays(90);
            stale = matters.stream()
                    .filter(m -> {
                        UUID leafId = m.getCurrentCourtCaseId();
                        if (leafId == null) return true;
                        LocalDate last = peshiMap.get(leafId);
                        return last == null || last.isBefore(cutoff);
                    })
                    .count();
        }

        int todayEvents = firmId != null
                ? courtEventRepository.findByFirmIdAndScheduledDate(firmId, LocalDate.now()).size()
                : courtEventRepository.findByScheduledDate(LocalDate.now()).size();

        return GlobalDashboardResponse.CaseStats.builder()
                .totalMatters(total)
                .activeMatters(active)
                .closedMatters(closed)
                .staleMatters(stale)
                .todayEvents(todayEvents)
                .build();
    }

    private GlobalDashboardResponse.ScraperStats buildScraperStats() {
        long courts = courtRepository.count();
        long daily = dailyHearingRepository.count();
        long weekly = weeklyHearingRepository.count();
        long matches = hearingMatchRepository.count();

        LocalDateTime lastScrape = dailyHearingRepository.findMaxScrapedDate()
                .map(d -> d.atStartOfDay())
                .orElse(null);

        return GlobalDashboardResponse.ScraperStats.builder()
                .courtsTracked(courts)
                .totalDailyHearings(daily)
                .totalWeeklyHearings(weekly)
                .totalMatches(matches)
                .lastScrapeTime(lastScrape)
                .build();
    }

    private List<GlobalDashboardResponse.RecentActivity> buildRecentActivity(UUID firmId) {
        List<AuditLog> logs = (firmId != null
                ? auditLogRepository.findRecentByFirm(firmId, PageRequest.of(0, 10))
                : auditLogRepository.findRecent(PageRequest.of(0, 10)))
                .getContent();

        List<UUID> userIds = logs.stream()
                .map(AuditLog::getUserId)
                .distinct()
                .collect(Collectors.toList());
        var userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return logs.stream()
                .map(log -> GlobalDashboardResponse.RecentActivity.builder()
                        .summary(log.getSummary())
                        .action(log.getAction() != null ? log.getAction().name() : null)
                        .entityType(log.getEntityType() != null ? log.getEntityType().name() : null)
                        .userName(userMap.get(log.getUserId()))
                        .createdAt(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Trend computation — cumulative daily growth over the requested window
    // ═══════════════════════════════════════════════════════════════════════

    private List<GlobalDashboardResponse.UserTrend> buildUserTrends(UUID firmId, int days) {
        LocalDateTime from = LocalDate.now().minusDays(days).atStartOfDay();
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();

        // Query: daily new user counts in the window
        List<Object[]> rows = firmId != null
                ? userRepository.countDailyByFirmIdAndDateRange(firmId, from, to)
                : userRepository.countDailyByDateRange(from, to);

        // Index by date for O(1) lookup
        Map<LocalDate, long[]> dailyMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            dailyMap.put(date, new long[]{
                    ((Number) row[1]).longValue(), // total
                    ((Number) row[2]).longValue(), // active
                    ((Number) row[3]).longValue(), // inactive
                    ((Number) row[4]).longValue()  // clients
            });
        }

        // Build cumulative series
        List<GlobalDashboardResponse.UserTrend> trends = new ArrayList<>();
        long cumTotal = 0, cumActive = 0, cumInactive = 0, cumClients = 0;
        for (LocalDate date : allDatesInRange(days)) {
            long[] day = dailyMap.getOrDefault(date, new long[4]);
            cumTotal    += day[0];
            cumActive   += day[1];
            cumInactive += day[2];
            cumClients  += day[3];
            trends.add(GlobalDashboardResponse.UserTrend.builder()
                    .date(date)
                    .totalUsers(cumTotal)
                    .activeUsers(cumActive)
                    .inactiveUsers(cumInactive)
                    .clients(cumClients)
                    .build());
        }
        return trends;
    }

    private List<GlobalDashboardResponse.MatterTrend> buildMatterTrends(UUID firmId, int days) {
        LocalDateTime from = LocalDate.now().minusDays(days).atStartOfDay();
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();

        List<Object[]> rows = firmId != null
                ? matterRepository.countDailyByFirmIdAndDateRange(firmId, from, to)
                : matterRepository.countDailyByDateRange(from, to);

        Map<LocalDate, long[]> dailyMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            dailyMap.put(date, new long[]{
                    ((Number) row[1]).longValue(), // total
                    ((Number) row[2]).longValue(), // active
                    ((Number) row[3]).longValue()  // closed
            });
        }

        List<GlobalDashboardResponse.MatterTrend> trends = new ArrayList<>();
        long cumTotal = 0, cumActive = 0, cumClosed = 0;
        for (LocalDate date : allDatesInRange(days)) {
            long[] day = dailyMap.getOrDefault(date, new long[3]);
            cumTotal  += day[0];
            cumActive += day[1];
            cumClosed += day[2];
            trends.add(GlobalDashboardResponse.MatterTrend.builder()
                    .date(date)
                    .totalMatters(cumTotal)
                    .activeMatters(cumActive)
                    .closedMatters(cumClosed)
                    .staleMatters(0) // stale is computed on-demand, not historically
                    .build());
        }
        return trends;
    }

    private List<GlobalDashboardResponse.FirmTrend> buildFirmTrends(int days) {
        LocalDateTime from = LocalDate.now().minusDays(days).atStartOfDay();
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();

        List<Object[]> rows = firmRepository.countDailyByDateRange(from, to);

        Map<LocalDate, long[]> dailyMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            dailyMap.put(date, new long[]{
                    ((Number) row[1]).longValue(), // total
                    ((Number) row[2]).longValue(), // active
                    ((Number) row[3]).longValue()  // suspended
            });
        }

        List<GlobalDashboardResponse.FirmTrend> trends = new ArrayList<>();
        long cumTotal = 0, cumActive = 0, cumSuspended = 0;
        for (LocalDate date : allDatesInRange(days)) {
            long[] day = dailyMap.getOrDefault(date, new long[3]);
            cumTotal     += day[0];
            cumActive    += day[1];
            cumSuspended += day[2];
            trends.add(GlobalDashboardResponse.FirmTrend.builder()
                    .date(date)
                    .totalFirms(cumTotal)
                    .activeFirms(cumActive)
                    .suspendedFirms(cumSuspended)
                    .build());
        }
        return trends;
    }

    /** Generate every date in the last N days (inclusive of today). */
    private List<LocalDate> allDatesInRange(int days) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dates.add(d);
        }
        return dates;
    }

    private UUID getRequiredFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }
}
