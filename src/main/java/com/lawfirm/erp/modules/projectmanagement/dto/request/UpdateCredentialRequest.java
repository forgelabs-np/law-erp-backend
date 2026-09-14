package com.lawfirm.erp.modules.projectmanagement.dto.request;

import lombok.Data;

@Data
public class UpdateCredentialRequest {

    private String siteName;

    private String siteType;

    private String siteUrl;

    private String usernameOrEmail;

    /** If provided, re-encrypts the password. */
    private String password;

    private String contactPerson;

    private String contactPhone;

    private String contactEmail;

    private String notes;
}
