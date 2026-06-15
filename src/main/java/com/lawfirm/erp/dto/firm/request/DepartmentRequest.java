package com.lawfirm.erp.dto.firm.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class DepartmentRequest {
    private UUID id;

    @NotBlank(message = "Department name is required")
    @Size(min = 2, max = 50)
    private String name;

    @NotBlank(message = "Department code is required")
    @Pattern(regexp = "^[A-Z_]+$", message = "Code must be uppercase with underscores")
    @Size(min = 2, max = 30)
    private String code;

    @Size(max = 200)
    private String description;

    private Integer displayOrder;
    private Boolean isActive;
}
