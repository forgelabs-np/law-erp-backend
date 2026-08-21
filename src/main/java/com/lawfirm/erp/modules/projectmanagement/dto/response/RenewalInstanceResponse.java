package com.lawfirm.erp.modules.projectmanagement.dto.response;

import com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class RenewalInstanceResponse {

    private Long id;
    private LocalDate dueDate;
    private RenewalInstanceStatus status;
    private LocalDateTime completedAt;
    private String completedByName;
    private String notes;
}
