package com.lawfirm.erp.modules.usermanagement.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class EmployeeDashboardResponse {

    private MyCaseStats myCaseStats;
    private List<MyTodayEvent> myTodayEvents;
    private List<MyUpcomingHearing> myUpcomingHearings;
    private List<MyUpcomingDeadline> myUpcomingDeadlines;
    private List<MyStaleMatter> myStaleMatters;
    private List<RecentUpdate> recentUpdates;

    @Data
    @Builder
    public static class MyCaseStats {
        private long openMatters;
        private long upcomingHearings;
        private long staleMatters;
    }

    @Data
    @Builder
    public static class MyTodayEvent {
        private String matterTitle;
        private String ourCourtCaseRef;
        private String courtRoom;
        private String scheduledTime;
        private String judgeName;
        private String status;
    }

    @Data
    @Builder
    public static class MyUpcomingHearing {
        private String matterTitle;
        private String ourCourtCaseRef;
        private LocalDate hearingDate;
        private String courtName;
        private int daysUntil;
    }

    @Data
    @Builder
    public static class MyUpcomingDeadline {
        private String matterTitle;
        private String ourCourtCaseRef;
        private LocalDate deadline;
        private String deadlineType;
        private int daysRemaining;
        private boolean isUrgent;
    }

    @Data
    @Builder
    public static class MyStaleMatter {
        private String matterTitle;
        private String ourCourtCaseRef;
        private LocalDate lastHearingDate;
        private int daysSinceLastHearing;
    }

    @Data
    @Builder
    public static class RecentUpdate {
        private String matterTitle;
        private String ourCourtCaseRef;
        private String updateType;
        private String description;
        private LocalDateTime createdAt;
    }
}
