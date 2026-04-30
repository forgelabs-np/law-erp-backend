package com.lawfirm.erp.dto.Tenant.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TenantTypeRequest {
    private Long id;

    @NotBlank(message = "Tenant type name is required")
    private String name;

    @NotBlank(message = "Tenant type code is required")
    private String code;

    private String description;
    private Boolean isActive;
}