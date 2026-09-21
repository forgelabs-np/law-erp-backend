package com.lawfirm.erp.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Self-service recovery start. {@code username} accepts either the login name or the
 * account e-mail — the response is identical either way, so it cannot be used to probe
 * which accounts exist.
 */
@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "Firm code is required")
    private String lawFirmCode;

    @NotBlank(message = "Username or email is required")
    private String username;
}
