package com.lawfirm.erp.modules.usermanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "New password is required")
    @Size(min = 6, max = 50, message = "Password must be 6-50 characters")
    private String newPassword;
}
