package com.lawfirm.erp.modules.usermanagement.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.FirmContextHolder;
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
import java.util.List;
import java.util.UUID;
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
        boolean superAdmin = currentUserResolver.isSuperAdmin();
        UUID firmId = superAdmin ? null : getRequiredFirmId();

        return GlobalDashboardResponse.builder()
                .userStats(buildUserStats(firmId))
                .firmStats(buildFirmStats(firmId))
                .caseStats(buildCaseStats(firmId))
                .scraperStats(buildScraperStats())
                .recentActivity(buildRecentActivity(firmId))
                .build();
    }

    private GlobalDashboardResponse.UserStats buildUserStats(UUID firmId) {
        // Use batch count queries instead of loading all users (avoids N+1 on role access)
        long total, active, inactive;
        List<User> users;
        if (firmId != null) {
            users = userRepository.findByFirmId(firmId);
            total = users.size();
            active = users.stream().filter(User::isActive).count();
        } else {
            users = userRepository.findAll();
            total = users.size();
            active = users.stream().filter(User::isActive).count();
        }
        inactive = total - active;

        long advocates = users.stream()
                .filter(u -> u.getRole() != null && "ADVOCATE".equals(u.getRole().getRoleCode()))
                .count();
        long paralegals = users.stream()
                .filter(u -> u.getRole() != null && "PARALEGAL".equals(u.getRole().getRoleCode()))
                .count();
        long clients = users.stream()
                .filter(u -> u.getUserType() == UserType.CLIENT)
                .count();
        long firmAdmins = users.stream()
                .filter(u -> u.getRole() != null && "FIRM_ADMIN".equals(u.getRole().getRoleCode()))
                .count();

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
        List<Firm> firms = firmId != null
                ? firmRepository.findAllById(List.of(firmId))
                : firmRepository.findAll();

        long total = firms.size();
        long active = firms.stream()
                .filter(f -> f.getStatus() == com.lawfirm.erp.common.enums.FirmStatus.ACTIVE)
                .count();
        return GlobalDashboardResponse.FirmStats.builder()
                .totalFirms(total)
                .activeFirms(active)
                .suspendedFirms(total - active)
                .build();
    }

    private GlobalDashboardResponse.CaseStats buildCaseStats(UUID firmId) {
        List<Matter> matters = firmId != null
                ? matterRepository.findByFirmId(firmId, org.springframework.data.domain.Pageable.unpaged()).getContent()
                : matterRepository.findAll();

        long total = matters.size();
        long active = matters.stream().filter(m -> m.getStatus() == MatterStatus.ACTIVE).count();
        long closed = matters.stream().filter(m -> m.getStatus() == MatterStatus.CLOSED).count();

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

    private UUID getRequiredFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }
}
