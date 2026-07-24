package com.lawfirm.erp.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SuperAdminLoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    @Pattern(regexp = "^[0-9]{6}$", message = "TOTP code must be exactly 6 digits")
    private String totpCode;
}