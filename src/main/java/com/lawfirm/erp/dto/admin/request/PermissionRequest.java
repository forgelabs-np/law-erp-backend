package com.lawfirm.erp.dto.admin.request;

import com.lawfirm.erp.common.enums.PermissionAction;
import com.lawfirm.erp.common.enums.PermissionScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
public class PermissionRequest {
    private UUID id;

    @NotBlank(message = "Module code is required")
    private String moduleCode;

    @NotNull(message = "Action is required")
    private PermissionAction action;

    @NotNull(message = "Scope is required")
    private PermissionScope scope = PermissionScope.TENANT;

    @NotBlank(message = "Permission code is required")
    private String code;  // Will be auto-generated if not provided

    private String description;
    private Boolean isActive;
}