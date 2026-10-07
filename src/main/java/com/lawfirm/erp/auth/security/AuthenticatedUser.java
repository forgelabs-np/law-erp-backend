package com.lawfirm.erp.auth.security;

import lombok.Data;

import java.util.List;
import java.util.UUID;

// Plain request-scoped DTO: JwtAuthFilter builds one per request and stashes it as the
// "authenticatedUser" request attribute. It is deliberately NOT a Spring bean — nothing injects it.
@Data
public class AuthenticatedUser {
    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private UUID firmId;
    private String firmCode;
    private String userType;
    private List<String> roles;
    private List<String> permissions;
    private String accessToken;

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(userType);
    }

    public boolean isFirmAdmin() {
        return "FIRM".equals(userType);
    }

    public boolean isFirmUser() {
        return "FIRM_USER".equals(userType);
    }

    public boolean isClient() {
        return "CLIENT".equals(userType);
    }
}