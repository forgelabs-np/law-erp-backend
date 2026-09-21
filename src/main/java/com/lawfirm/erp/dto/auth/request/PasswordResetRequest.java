package com.lawfirm.erp.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Redeems the one-time token e-mailed by {@code POST /auth/forgot-password}.
 * The password rules here are the single policy for the whole product (8–50 chars);
 * they deliberately match {@link ChangePasswordRequest}.
 */
@Data
public class PasswordResetRequest {

    @NotBlank(message = "Reset token is required")
    private String token;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 50, message = "Password must be 8-50 characters")
    private String newPassword;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;
}
