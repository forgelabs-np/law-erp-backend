package com.lawfirm.erp.dto.admin.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class ModuleRequest {
    private UUID id;

    @NotBlank(message = "Module name is required")
    @Size(min = 2, max = 50, message = "Module name must be between 2 and 50 characters")
    private String name;

    @NotBlank(message = "Module code is required")
    @Size(min = 2, max = 50, message = "Module code must be between 2 and 50 characters")
    @Pattern(regexp = "^[A-Z_]+$", message = "Module code must be uppercase with underscores")
    private String code;

    @Size(max = 200, message = "Description cannot exceed 200 characters")
    private String description;

    private Integer displayOrder = 0;
    private String icon;
    private String path;
    private Boolean isActive;
}