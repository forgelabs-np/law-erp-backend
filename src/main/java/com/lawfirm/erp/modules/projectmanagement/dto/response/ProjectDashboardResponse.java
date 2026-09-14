package com.lawfirm.erp.modules.projectmanagement.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProjectDashboardResponse {

    private long totalProjects;
    private long activeProjects;
    private long onHoldProjects;
    private long completedProjects;
    private long totalCredentials;
    private long totalRenewals;
    private long overdueInstances;
    private long upcomingInstances;

    @Builder.Default
    private List<OverdueItem> overdueItems = List.of();

    @Builder.Default
    private List<UpcomingItem> upcomingItems = List.of();

    @Data
    @Builder
    public static class OverdueItem {
        private String projectCode;
        private String projectName;
        private String renewalTitle;
        private String renewalTypeName;
        private java.time.LocalDate dueDate;
        private int daysOverdue;
    }

    @Data
    @Builder
    public static class UpcomingItem {
        private String projectCode;
        private String projectName;
        private String renewalTitle;
        private String renewalTypeName;
        private java.time.LocalDate dueDate;
        private int daysUntilDue;
    }
}
