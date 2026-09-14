package com.lawfirm.erp.modules.projectmanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateRenewalTypeRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;
}
