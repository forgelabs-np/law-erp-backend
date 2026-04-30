package com.lawfirm.erp.dto.admin.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ModuleRequest {
    private Long id;

    @NotBlank(message = "Module name is required")
    private String name;

    @NotBlank(message = "Module code is required")
    private String code;

    private String description;
    private String icon;
    private Integer displayOrder;
    private Boolean isActive;
}
