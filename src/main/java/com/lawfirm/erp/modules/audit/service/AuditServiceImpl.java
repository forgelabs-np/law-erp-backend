package com.lawfirm.erp.modules.audit.service;

import com.lawfirm.erp.auth.security.AuthenticatedUser;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditServiceImpl implements AuditService {

    private final AsyncAuditWriter asyncAuditWriter;

    @Override
    public void log(AuditAction action, AuditEntity entityType,
                    UUID entityId, String summary) {
        AuthenticatedUser currentUser = getCurrentUser();
        if (currentUser == null) {
            log.debug("AuditService.log() skipped — no authenticated user. action={}", action);
            return;
        }

        String ip = getClientIp();

        asyncAuditWriter.write(
                currentUser.getFirmId(),
                currentUser.getId(),
                toUserTypeChar(currentUser),
                action, entityType, entityId,
                truncate(summary, 200),
                ip
        );
    }

    @Override
    public void logExplicit(UUID firmId, UUID userId, String userTypeChar,
                            AuditAction action, AuditEntity entityType,
                            UUID entityId, String summary, String ipAddress) {
        asyncAuditWriter.write(firmId, userId, userTypeChar, action, entityType, entityId,
                truncate(summary, 200), ipAddress);
    }

    private AuthenticatedUser getCurrentUser() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                AuthenticatedUser authUser = (AuthenticatedUser) attrs.getRequest()
                        .getAttribute("authenticatedUser");
                if (authUser != null) return authUser;
            }

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
                return user;
            }
        } catch (Exception e) {
            log.warn("Could not resolve current user for audit: {}", e.getMessage());
        }
        return null;
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;

            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private String toUserTypeChar(AuthenticatedUser user) {
        if (user.isSuperAdmin()) return "S";
        if (user.isFirmAdmin()) return "A";
        if (user.isClient()) return "C";
        return "F";
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
