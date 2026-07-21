package com.lawfirm.erp.modules.audit.service;

import com.lawfirm.erp.modules.audit.entity.AuditLog;
import com.lawfirm.erp.modules.audit.repository.AuditLogRepository;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Standalone async writer for audit logs.
 *
 * Extracted from AuditService to fix the self-invocation problem:
 * {@code AuditService.log() → this.writeAsync()} bypasses Spring's AOP proxy,
 * so the {@code @Async} annotation was never picked up.
 *
 * Now {@code AuditService} delegates to this injected bean, the call goes
 * through the Spring proxy, and {@code @Async} works correctly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AsyncAuditWriter {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void write(UUID firmId, UUID userId, String userTypeChar,
                      AuditAction action, AuditEntity entityType,
                      UUID entityId, String summary, String ipAddress) {
        try {
            AuditLog auditLog = AuditLog.of(
                    firmId, userId, userTypeChar,
                    action, entityType, entityId,
                    summary, ipAddress
            );
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to write audit log: action={}, entity={}, entityId={} — {}",
                    action, entityType, entityId, e.getMessage());
        }
    }
}
