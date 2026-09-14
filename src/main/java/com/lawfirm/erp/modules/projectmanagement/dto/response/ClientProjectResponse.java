package com.lawfirm.erp.modules.projectmanagement.dto.response;

import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ClientProjectResponse {

    private UUID id;
    private String projectCode;
    private String name;
    private String description;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate targetEndDate;

    @Builder.Default
    private List<RenewalInstanceResponse> upcomingRenewals = new ArrayList<>();

    private LocalDateTime createdAt;
}
