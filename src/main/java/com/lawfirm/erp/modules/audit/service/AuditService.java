package com.lawfirm.erp.modules.audit.service;

import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.auth.security.AuthenticatedUser;
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
public class AuditService {

    private final AsyncAuditWriter asyncAuditWriter;

    /**
     * Primary log method — resolves current user from SecurityContext.
     *
     * FIX: @Async methods run in a separate thread. By default Spring does NOT
     * propagate SecurityContext to async threads. We capture the user BEFORE
     * the async call (on the main request thread where SecurityContext is live),
     * then pass the resolved values into the async write.
     *
     * This is why all previous calls were logging "no authenticated user" —
     * the async thread had an empty SecurityContext.
     *
     * Solution: capture user synchronously, write asynchronously.
     */
    public void log(AuditAction action, AuditEntity entityType,
                    UUID entityId, String summary) {

        // Resolve user NOW — on the calling thread — where SecurityContext is live
        AuthenticatedUser currentUser = getCurrentUser();
        if (currentUser == null) {
            log.debug("AuditService.log() skipped — no authenticated user. action={}", action);
            return;
        }

        String ip = getClientIp();

        // Pass resolved values to async write — no SecurityContext needed in that thread
        asyncAuditWriter.write(
                currentUser.getFirmId(),
                currentUser.getId(),
                toUserTypeChar(currentUser),
                action, entityType, entityId,
                truncate(summary, 200),
                ip
        );
    }

    /**
     * Explicit log — use when:
     * - Called from a context with no authenticated user (firm creation by super admin async flow)
     * - Called from a scheduled job
     * - Called from auth endpoints (login) before SecurityContext is populated
     *
     * You pass firmId, userId, userTypeChar explicitly instead of reading from SecurityContext.
     */
    public void logExplicit(UUID firmId, UUID userId, String userTypeChar,
                            AuditAction action, AuditEntity entityType,
                            UUID entityId, String summary, String ipAddress) {
        asyncAuditWriter.write(firmId, userId, userTypeChar, action, entityType, entityId,
                truncate(summary, 200), ipAddress);
    }

    /** FIX: The auth principal from JwtAuthFilter is AuthenticatedDetail (wrapped in
     *  UsernamePasswordAuthenticationToken), NOT AuthenticatedUser.
     *  AuthenticatedUser is stored as a request attribute under key "authenticatedUser".
     *  We must read from the request attribute instead of SecurityContext principal. */
    private AuthenticatedUser getCurrentUser() {
        try {
            // First try: check request attribute (set by JwtAuthFilter)
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                AuthenticatedUser authUser = (AuthenticatedUser) attrs.getRequest()
                        .getAttribute("authenticatedUser");
                if (authUser != null) return authUser;
            }

            // Second try: SecurityContext principal (may be AuthenticatedDetail)
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
        if (user.isClient()) return "C";
        return "F";
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}