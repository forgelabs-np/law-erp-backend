package com.lawfirm.erp.modules.projectmanagement.dto.request;

import com.lawfirm.erp.modules.projectmanagement.enums.RenewalRecurrence;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateRenewalRequest {

    private Long renewalTypeId;

    private String title;

    private String description;

    private RenewalRecurrence recurrence;

    private LocalDate startDate;

    private LocalDate endDate;

    private UUID assignedToId;
}
