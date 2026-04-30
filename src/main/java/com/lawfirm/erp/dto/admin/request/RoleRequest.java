package com.lawfirm.erp.dto.admin.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class RoleRequest {
    private Long id;

    @NotBlank(message = "Role name is required")
    private String name;

    @NotBlank(message = "Role code is required")
    private String code;

    private String description;
    private List<RoleFeatureRequest> features;
}