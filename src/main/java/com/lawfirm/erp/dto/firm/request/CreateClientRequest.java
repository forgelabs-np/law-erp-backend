package com.lawfirm.erp.dto.firm.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateClientRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, numbers, dots, underscores and hyphens")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String mobileNo;

    @NotBlank(message = "Password is required")
    // One password policy for the whole product (8–50), the same the change/reset endpoints enforce.
    @Size(min = 8, max = 50, message = "Password must be 8-50 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100)
    private String fullName;

    private Boolean portalAccessEnabled = false;
}