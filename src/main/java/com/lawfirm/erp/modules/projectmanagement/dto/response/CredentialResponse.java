package com.lawfirm.erp.modules.projectmanagement.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CredentialResponse {

    private Long id;
    private String siteName;
    private String siteType;
    private String siteUrl;
    private String usernameOrEmail;
    /** Always masked in list/detail — use /reveal endpoint to get actual password. */
    @Builder.Default
    private String password = "••••••••";
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;
    private String notes;
    private LocalDateTime createdAt;
}
