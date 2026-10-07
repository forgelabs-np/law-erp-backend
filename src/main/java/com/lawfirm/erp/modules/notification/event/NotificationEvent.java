package com.lawfirm.erp.modules.notification.event;

import com.lawfirm.erp.modules.notification.enums.NotificationType;

import java.util.Map;
import java.util.UUID;

public record NotificationEvent(
        UUID firmId,
        UUID recipientUserId,
        String recipientRoleCode,
        boolean allFirmUsers,
        NotificationType type,
        String referenceType,
        UUID referenceId,
        String dedupKey,
        Map<String, Object> variables) {

    public NotificationEvent {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public static NotificationEvent toUser(UUID firmId, UUID recipientUserId, NotificationType type,
                                           String referenceType, UUID referenceId,
                                           Map<String, Object> variables) {
        return new NotificationEvent(firmId, recipientUserId, null, false, type,
                referenceType, referenceId, null, variables);
    }

    public static NotificationEvent toRole(UUID firmId, String recipientRoleCode, NotificationType type,
                                           String referenceType, UUID referenceId,
                                           Map<String, Object> variables) {
        return new NotificationEvent(firmId, null, recipientRoleCode, false, type,
                referenceType, referenceId, null, variables);
    }
}
