package com.lawfirm.erp.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    public AuthenticatedUser getCurrentUser() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            return (AuthenticatedUser) request.getAttribute("authenticatedUser");
        }
        return null;
    }

    public UUID getCurrentUserId() {
        AuthenticatedUser user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    public UUID getCurrentFirmId() {
        AuthenticatedUser user = getCurrentUser();
        return user != null ? user.getFirmId() : null;
    }

    public boolean isSuperAdmin() {
        AuthenticatedUser user = getCurrentUser();
        return user != null && user.isSuperAdmin();
    }

    public boolean isAuthenticated() {
        return getCurrentUser() != null;
    }
}