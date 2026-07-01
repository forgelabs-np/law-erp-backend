package com.lawfirm.erp.dto.firm.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateEmployeeRoleRequest {
    @NotNull(message = "Role ID is required")
    private UUID roleId;
}