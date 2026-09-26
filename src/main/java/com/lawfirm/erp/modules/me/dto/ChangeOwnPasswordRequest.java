package com.lawfirm.erp.modules.me.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A signed-in user changing their own password.
 *
 * <p>Distinct from {@code POST /auth/change-password}, which redeems the one-time token handed
 * out on a forced rotation: this one is reached from a profile screen with the session already
 * established, so it proves the caller with the password they are replacing.
 */
@Data
public class ChangeOwnPasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 50, message = "Password must be 8-50 characters")
    private String newPassword;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;
}
