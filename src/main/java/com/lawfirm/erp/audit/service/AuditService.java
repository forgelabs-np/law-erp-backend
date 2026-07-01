package com.lawfirm.erp.audit.service;

import com.lawfirm.erp.audit.entity.AuditLog;
import com.lawfirm.erp.audit.repository.AuditLogRepository;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * AuditService — single entry point for all audit logging.
 *
 * Usage pattern (call directly after the main operation succeeds):
 *
 *   // Simple — no entity ID needed:
 *   auditService.log(AuditAction.USER_DEACTIVATED, AuditEntity.USER,
 *                    user.getId(), "Deactivated employee: " + user.getUsername());
 *
 *   // After save — use the saved entity's ID:
 *   Case saved = caseRepo.save(newCase);
 *   auditService.log(AuditAction.CASE_CREATED, AuditEntity.CASE,
 *                    saved.getId(), "Created case: " + saved.getTitle());
 *
 * Writes are @Async — audit logging never blocks the main request thread.
 * If audit write fails, the main transaction is already committed — we log
 * the failure but don't roll back. Audit is observability, not business logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Primary log method — resolves current user from SecurityContext automatically.
     * Use this in 95% of cases.
     */
    @Async
    public void log(AuditAction action, AuditEntity entityType,
                    UUID entityId, String summary) {
        try {
            AuthenticatedUser currentUser = getCurrentUser();
            if (currentUser == null) {
                log.warn("AuditService.log() called with no authenticated user — skipping. action={}", action);
                return;
            }

            AuditLog auditLog = AuditLog.of(
                    currentUser.getFirmId(),
                    currentUser.getId(),
                    toUserTypeChar(currentUser),
                    action,
                    entityType,
                    entityId,
                    truncate(summary, 200),
                    getClientIp()
            );

            auditLogRepository.save(auditLog);

        } catch (Exception e) {
            // Audit must never break the main flow
            log.error("Failed to write audit log: action={}, entity={}, entityId={} — {}",
                    action, entityType, entityId, e.getMessage());
        }
    }

    /**
     * Explicit log — use when you need to override the current user context,
     * e.g. system-level events or async jobs.
     */
    @Async
    public void logExplicit(UUID firmId, UUID userId, String userTypeChar,
                            AuditAction action, AuditEntity entityType,
                            UUID entityId, String summary, String ipAddress) {
        try {
            AuditLog auditLog = AuditLog.of(
                    firmId, userId, userTypeChar,
                    action, entityType, entityId,
                    truncate(summary, 200), ipAddress
            );
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to write explicit audit log: action={} — {}", action, e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private AuthenticatedUser getCurrentUser() {
        try {
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

            // Check X-Forwarded-For first (reverse proxy / load balancer)
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim(); // first IP in the chain
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Maps UserType to single CHAR(1) stored in DB.
     * 'S' = SUPER_ADMIN
     * 'F' = FIRM_USER
     * 'C' = CLIENT
     */
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