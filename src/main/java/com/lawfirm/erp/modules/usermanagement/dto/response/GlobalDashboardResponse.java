package com.lawfirm.erp.modules.usermanagement.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class GlobalDashboardResponse {

    private UserStats userStats;
    private FirmStats firmStats;
    private CaseStats caseStats;
    private ScraperStats scraperStats;
    private List<RecentActivity> recentActivity;

    @Data
    @Builder
    public static class UserStats {
        private long totalUsers;
        private long activeUsers;
        private long inactiveUsers;
        private long totalAdvocates;
        private long totalParalegals;
        private long totalClients;
        private long totalFirmAdmins;
    }

    @Data
    @Builder
    public static class FirmStats {
        private long totalFirms;
        private long activeFirms;
        private long suspendedFirms;
    }

    @Data
    @Builder
    public static class CaseStats {
        private long totalMatters;
        private long activeMatters;
        private long closedMatters;
        private long staleMatters;
        private int todayEvents;
    }

    @Data
    @Builder
    public static class ScraperStats {
        private long courtsTracked;
        private long totalDailyHearings;
        private long totalWeeklyHearings;
        private long totalMatches;
        private LocalDateTime lastScrapeTime;
    }

    @Data
    @Builder
    public static class RecentActivity {
        private String summary;
        private String action;
        private String entityType;
        private String userName;
        private LocalDateTime createdAt;
    }
}
