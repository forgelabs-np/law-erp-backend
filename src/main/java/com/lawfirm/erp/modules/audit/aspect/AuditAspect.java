package com.lawfirm.erp.modules.audit.aspect;

import com.lawfirm.erp.modules.audit.annotation.Audit;
import com.lawfirm.erp.modules.audit.entity.AuditLog;
import com.lawfirm.erp.modules.audit.repository.AuditLogRepository;
import com.lawfirm.erp.modules.audit.util.AuditSpelHelper;
import com.lawfirm.erp.auth.security.AuthenticatedUser;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserResolver currentUserResolver;

    @Around("@annotation(com.lawfirm.erp.modules.audit.annotation.Audit)")
    public Object logAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Audit audit = method.getAnnotation(Audit.class);

        if (audit.skipIfNullResult() && result == null) {
            return result;
        }

        Map<String, Object> params = AuditSpelHelper.buildParameterMap(method, joinPoint.getArgs());

        Object entityIdObj = AuditSpelHelper.evaluate(audit.entityId(), result, params);
        String summary = (String) AuditSpelHelper.evaluate(audit.summary(), result, params);

        UUID entityId = null;
        if (entityIdObj != null) {
            if (entityIdObj instanceof UUID) {
                entityId = (UUID) entityIdObj;
            } else if (entityIdObj instanceof String) {
                try {
                    entityId = UUID.fromString((String) entityIdObj);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        AuthenticatedUser user = getCurrentUser();
        if (user == null) {
            log.warn("AuditAspect: No authenticated user found for method {}", method.getName());
            return result;
        }

        String ip = getClientIp();

        saveAuditLogAsync(
                user.getFirmId(),
                user.getId(),
                toUserTypeChar(user),
                audit.action(),
                audit.entity(),
                entityId,
                summary != null ? truncate(summary, 200) : null,
                ip
        );

        return result;
    }

    @Async
    protected void saveAuditLogAsync(UUID firmId, UUID userId, String userTypeChar,
                                     com.lawfirm.erp.common.enums.AuditAction action,
                                     com.lawfirm.erp.common.enums.AuditEntity entityType,
                                     UUID entityId, String summary, String ip) {
        try {
            AuditLog auditLog = AuditLog.of(
                    firmId, userId, userTypeChar,
                    action, entityType, entityId,
                    summary, ip
            );
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log asynchronously: action={}, entity={} — {}",
                    action, entityType, e.getMessage());
        }
    }

    private AuthenticatedUser getCurrentUser() {
        try {
            AuthenticatedUser fromRequest = currentUserResolver.getCurrentUser();
            if (fromRequest != null) {
                return fromRequest;
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
        return "F";
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}