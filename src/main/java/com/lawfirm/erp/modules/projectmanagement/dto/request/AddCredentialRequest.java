package com.lawfirm.erp.modules.projectmanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddCredentialRequest {

    @NotBlank(message = "Site name is required")
    private String siteName;

    private String siteType;

    private String siteUrl;

    @NotBlank(message = "Username or email is required")
    private String usernameOrEmail;

    @NotBlank(message = "Password is required")
    private String password;

    private String contactPerson;

    private String contactPhone;

    private String contactEmail;

    private String notes;
}
