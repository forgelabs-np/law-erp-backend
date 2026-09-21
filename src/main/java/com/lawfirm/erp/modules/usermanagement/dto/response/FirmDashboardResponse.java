package com.lawfirm.erp.modules.usermanagement.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class FirmDashboardResponse {

    private CaseStats caseStats;
    private List<TodayEvent> todayEvents;
    private List<UpcomingHearing> upcomingHearings;
    private InvoiceStats invoiceStats;
    private List<OverdueInvoice> overdueInvoices;
    private RenewalStats renewalStats;
    private List<UpcomingRenewal> upcomingRenewals;
    private List<TeamCaseload> teamCaseload;
    private List<RecentActivity> recentActivity;

    @Data
    @Builder
    public static class CaseStats {
        private long totalMatters;
        private long activeMatters;
        private long dormantMatters;
        private long closedMatters;
        private long staleMatters;
    }

    @Data
    @Builder
    public static class TodayEvent {
        private String matterTitle;
        private String courtRoom;
        private String scheduledTime;
        private String judgeName;
        private String attendingAdvocateName;
    }

    @Data
    @Builder
    public static class UpcomingHearing {
        private String matterTitle;
        private LocalDate hearingDate;
        private String courtName;
        private int daysUntil;
    }

    @Data
    @Builder
    public static class InvoiceStats {
        private long totalInvoices;
        private long outstanding;
        private long overdue;
        private BigDecimal totalOutstandingAmount;
        private BigDecimal overdueAmount;
    }

    @Data
    @Builder
    public static class OverdueInvoice {
        private String invoiceNumber;
        private String clientName;
        private BigDecimal amount;
        private int daysOverdue;
    }

    @Data
    @Builder
    public static class RenewalStats {
        private long dueThisMonth;
        private long overdue;
    }

    @Data
    @Builder
    public static class UpcomingRenewal {
        private String projectName;
        private String renewalTitle;
        private LocalDate dueDate;
        private int daysUntil;
    }

    @Data
    @Builder
    public static class TeamCaseload {
        private String advocateName;
        private long openMatters;
    }

    @Data
    @Builder
    public static class RecentActivity {
        private String summary;
        private String userName;
        private LocalDateTime createdAt;
    }
}
