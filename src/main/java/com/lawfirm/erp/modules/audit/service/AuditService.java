package com.lawfirm.erp.modules.audit.service;

import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;

import java.util.UUID;

public interface AuditService {

    void log(AuditAction action, AuditEntity entityType, UUID entityId, String summary);

    void logExplicit(UUID firmId, UUID userId, String userTypeChar,
                     AuditAction action, AuditEntity entityType,
                     UUID entityId, String summary, String ipAddress);
}
