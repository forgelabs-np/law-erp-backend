package com.lawfirm.erp.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MfaSetupConfirmRequest {
    @NotBlank(message = "MFA token is required")
    private String mfaToken;

    @NotBlank(message = "TOTP code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "TOTP code must be exactly 6 digits")
    private String totpCode;
}