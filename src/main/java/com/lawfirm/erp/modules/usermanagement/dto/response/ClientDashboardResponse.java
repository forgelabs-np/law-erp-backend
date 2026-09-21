package com.lawfirm.erp.modules.usermanagement.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ClientDashboardResponse {

    private MyMatterStats myMatterStats;
    private List<MyMatter> myMatters;
    private MyNextHearing myNextHearing;
    private List<MyUpcomingEvent> myUpcomingEvents;
    private MyInvoiceStats myInvoiceStats;
    private List<MyOutstandingInvoice> myOutstandingInvoices;
    private List<MyRecentUpdate> myRecentUpdates;

    @Data
    @Builder
    public static class MyMatterStats {
        private long totalMatters;
        private long activeMatters;
        private long closedMatters;
    }

    @Data
    @Builder
    public static class MyMatter {
        private String matterNumber;
        private String title;
        private String status;
        private String courtName;
        private String ourCourtCaseRef;
        private LocalDate lastUpdate;
        private LocalDate nextHearingDate;
    }

    @Data
    @Builder
    public static class MyNextHearing {
        private String matterTitle;
        private String ourCourtCaseRef;
        private LocalDate hearingDate;
        private String courtName;
        private int daysUntil;
    }

    @Data
    @Builder
    public static class MyUpcomingEvent {
        private String matterTitle;
        private String ourCourtCaseRef;
        private LocalDate eventDate;
        private String eventType;
        private String courtRoom;
    }

    @Data
    @Builder
    public static class MyInvoiceStats {
        private long totalInvoices;
        private long outstanding;
        private BigDecimal outstandingAmount;
    }

    @Data
    @Builder
    public static class MyOutstandingInvoice {
        private String invoiceNumber;
        private BigDecimal amount;
        private LocalDate dueDate;
        private int daysUntil;
    }

    @Data
    @Builder
    public static class MyRecentUpdate {
        private String matterTitle;
        private String ourCourtCaseRef;
        private String updateType;
        private String description;
        private LocalDateTime createdAt;
    }
}
