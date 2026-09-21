package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStage;
import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.scraper.enums.HearingSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Comprehensive dashboard response for lawyers combining case management and scraper data.
 * Designed to make lawyers' lives easier by providing all important information in one place.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LawyerDashboardResponse {

    /**
     * Summary statistics for the lawyer
     */
    private DashboardStats stats;

    /**
     * Cases requiring immediate attention
     */
    private List<CriticalCase> criticalCases;

    /**
     * Upcoming hearings from both scraper and internal court events
     */
    private List<UpcomingHearing> upcomingHearings;

    /**
     * Upcoming deadlines
     */
    private List<UpcomingDeadline> upcomingDeadlines;

    /**
     * Recent case status updates
     */
    private List<CaseUpdate> recentUpdates;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DashboardStats {
        private Long totalActiveCases;
        private Long casesWithUpcomingHearings;
        private Long casesWithPendingDeadlines;
        private Long casesAwaitingJudgment;
        private Long casesNeedingAttention;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CriticalCase {
        private UUID courtCaseId;
        private String ourCourtCaseRef;
        private String matterTitle;
        private String courtName;
        private CourtLevel courtLevel;
        private CourtCaseStage currentStage;
        private String status;
        private LocalDate nextHearingDate;
        private String nextHearingDateBs;
        private LocalDate deadline;
        private String deadlineType;
        private String urgencyReason;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UpcomingHearing {
        private UUID courtCaseId;
        private String ourCourtCaseRef;
        private String matterTitle;
        private String courtName;
        private LocalDate hearingDate;
        private String hearingDateBs;
        private String time;
        private String judgeName;
        private String subject;
        private String orderType;
        private HearingSource source;
        private String sourceDescription;
        private boolean isToday;
        private boolean isTomorrow;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UpcomingDeadline {
        private UUID courtCaseId;
        private String ourCourtCaseRef;
        private String matterTitle;
        private String courtName;
        private LocalDate deadline;
        private String deadlineType;
        private String description;
        private Integer daysRemaining;
        private boolean isUrgent;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CaseUpdate {
        private UUID courtCaseId;
        private String ourCourtCaseRef;
        private String matterTitle;
        private String courtName;
        private String updateType;
        private String updateDescription;
        private LocalDateTime updateTimestamp;
        private boolean requiresAttention;
    }

    // Additional response DTOs for specific endpoints
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CaseSummary {
        private UUID advocateId;
        private String advocateName;
        private List<CriticalCase> cases;
        private DashboardStats stats;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HearingSummary {
        private UUID advocateId;
        private String advocateName;
        private List<UpcomingHearing> hearings;
        private Integer totalHearings;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DeadlineSummary {
        private UUID advocateId;
        private String advocateName;
        private List<UpcomingDeadline> deadlines;
        private Integer totalDeadlines;
        private Integer urgentDeadlines;
    }
}
