package com.lawfirm.erp.modules.projectmanagement.dto.response;

import com.lawfirm.erp.modules.projectmanagement.enums.RenewalRecurrence;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class RenewalResponse {

    private Long id;
    private Long renewalTypeId;
    private String renewalTypeName;
    private String title;
    private String description;
    private RenewalRecurrence recurrence;
    private LocalDate startDate;
    private LocalDate endDate;
    private String assignedToName;
    private RenewalStatus status;

    @Builder.Default
    private List<RenewalInstanceResponse> instances = new ArrayList<>();

    private LocalDateTime createdAt;
}
