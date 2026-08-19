package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.CourtEventStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class DashboardResponse {

    private DashboardStats stats;
    private List<TodayEventSummary> todayEvents;
    private List<CasePositioningResponse> casePositioning;
    private List<CasePositioningResponse> staleCases;

    @Data
    @Builder
    public static class DashboardStats {
        private long totalMatters;
        private long activeMatters;
        private long dormantMatters;
        private long closedMatters;
        private int todayEventsCount;
        private int staleCount;
    }

    @Data
    @Builder
    public static class TodayEventSummary {
        private UUID eventId;
        private String ourCourtCaseRef;
        private String matterNumber;
        private String matterTitle;
        private CourtEventType eventType;
        private LocalDate scheduledDate;
        private LocalTime scheduledTime;
        private LocalTime endTime;
        private CourtEventStatus status;
        private String courtRoom;
        private String judgeName;
        private UUID attendingAdvocateId;
        private String attendingAdvocateName;
    }
}
