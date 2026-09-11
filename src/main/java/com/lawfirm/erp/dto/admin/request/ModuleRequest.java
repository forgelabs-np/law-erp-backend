package com.lawfirm.erp.dto.admin.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ModuleRequest {
    private UUID id;

    @NotBlank(message = "Module name is required")
    @Size(min = 2, max = 50)
    private String name;

    @NotBlank(message = "Module code is required")
    @Pattern(regexp = "^[A-Z_]+$", message = "Module code must be uppercase with underscores")
    @Size(min = 2, max = 50)
    private String code;

    @Size(max = 200)
    private String description;

    private UUID parentId;
    private Integer displayOrder;
    private Integer sortOrder;  // ← ADD THIS
    private String icon;
    private String path;
    private Boolean isActive;

    private List<UUID> permissionIds;
}