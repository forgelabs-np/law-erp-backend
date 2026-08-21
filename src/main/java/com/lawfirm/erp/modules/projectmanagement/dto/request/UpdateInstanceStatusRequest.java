package com.lawfirm.erp.modules.projectmanagement.dto.request;

import com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateInstanceStatusRequest {

    @NotNull(message = "Status is required")
    private RenewalInstanceStatus status;

    private String notes;
}
