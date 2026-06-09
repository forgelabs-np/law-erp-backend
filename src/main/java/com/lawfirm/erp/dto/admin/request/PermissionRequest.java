package com.lawfirm.erp.dto.admin.request;

import com.lawfirm.erp.common.enums.PermissionAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class PermissionRequest {
    private UUID id;

    @NotNull(message = "Module ID is required")
    private UUID moduleId;

    @NotNull(message = "Action is required")
    private PermissionAction action;

    @NotBlank(message = "Permission code is required")
    private String code;

    private String description;
    private Boolean isActive;
}