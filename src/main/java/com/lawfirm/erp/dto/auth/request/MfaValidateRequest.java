// ── MfaValidateRequest.java ───────────────────────────────────────────────────
// POST /api/v1/auth/mfa/validate
// Called when user already has MFA set up and needs to enter TOTP on login
package com.lawfirm.erp.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class MfaValidateRequest {

    @NotBlank(message = "MFA token is required")
    private String mfaToken;          // the short-lived token from login step 1

    @NotBlank(message = "TOTP code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "TOTP code must be exactly 6 digits")
    private String totpCode;           // 6-digit code from Google Authenticator
}
