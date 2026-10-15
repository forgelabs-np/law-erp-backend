package com.lawfirm.erp.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "Firm code is required")
    private String lawFirmCode;

    @NotBlank(message = "Username or email is required")
    private String username;
}
