package com.lawfirm.erp.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthenticatedDetail {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private String tenantSubdomain;
    private String accessToken;
}
