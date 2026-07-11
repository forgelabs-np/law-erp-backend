package com.lawfirm.erp.modules.usermanagement.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BulkDeactivateRequest {
    @NotEmpty(message = "At least one user ID is required")
    private List<UUID> userIds;
}
