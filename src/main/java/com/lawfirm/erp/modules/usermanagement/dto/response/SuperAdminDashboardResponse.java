package com.lawfirm.erp.modules.usermanagement.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SuperAdminDashboardResponse {

    private FirmStats firmStats;
    private UserStats userStats;
    private CaseStats caseStats;
    private ScraperStats scraperStats;
    private TrialAlerts trialAlerts;
    private List<RecentActivity> recentActivity;
    private List<MatterTrend> matterTrends;

    @Data
    @Builder
    public static class FirmStats {
        private long totalFirms;
        private long activeFirms;
        private long suspendedFirms;
        private long trialFirms;
    }

    @Data
    @Builder
    public static class UserStats {
        private long totalUsers;
        private long activeUsers;
        private Map<String, Long> byRole;
    }

    @Data
    @Builder
    public static class CaseStats {
        private long totalMatters;
        private long activeMatters;
        private long closedMatters;
    }

    @Data
    @Builder
    public static class ScraperStats {
        private long courtsTracked;
        private long totalHearings;
        private long totalMatches;
        private LocalDateTime lastScrapeTime;
    }

    @Data
    @Builder
    public static class TrialAlerts {
        private long expiringThisWeek;
        private long expired;
    }

    @Data
    @Builder
    public static class RecentActivity {
        private String summary;
        private String action;
        private String userName;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    public static class MatterTrend {
        private LocalDate date;
        private long total;
        private long active;
        private long closed;
    }
}
