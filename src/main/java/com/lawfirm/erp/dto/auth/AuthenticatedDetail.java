package com.lawfirm.erp.dto.auth;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class AuthenticatedDetail {
    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private String userType;
    private String firmId;
    private String firmCode;
    private String accessToken;
}