package com.lawfirm.erp.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SuperAdminLoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}