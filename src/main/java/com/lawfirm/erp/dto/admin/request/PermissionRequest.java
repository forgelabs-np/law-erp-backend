package com.lawfirm.erp.dto.admin.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.UUID;

@Data
public class PermissionRequest {
    private UUID id;

    @NotBlank(message = "Permission name is required")
    private String name;

    @NotBlank(message = "Permission code is required")
    private String code;

    private String description;
    private Boolean isActive;
}