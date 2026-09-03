package com.lawfirm.erp.auth.security;

import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.List;
import java.util.UUID;

@Data
@Component
@RequestScope
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