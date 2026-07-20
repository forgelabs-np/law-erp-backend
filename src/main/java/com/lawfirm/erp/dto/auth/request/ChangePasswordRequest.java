package com.lawfirm.erp.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Password change token is required")
    private String passwordChangeToken;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 50, message = "Password must be 8-50 characters")
    private String newPassword;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;
}