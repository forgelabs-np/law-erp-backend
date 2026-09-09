package com.lawfirm.erp.modules.notification.event;

import com.lawfirm.erp.modules.notification.enums.NotificationType;

import java.util.Map;
import java.util.UUID;

/**
 * Published by domain modules (case management, invoice, …) via
 * ApplicationEventPublisher. Producers know nothing about persistence,
 * channels, or the bell icon — exactly one targeting mode must be set.
 *
 * Swapping an in-process bus for a broker later means replacing the
 * listener, never the producers.
 */
public record NotificationEvent(
        UUID firmId,
        UUID recipientUserId,        // direct: notify this exact user
        String recipientRoleCode,    // fan-out: all users with this role code in the firm
        boolean allFirmUsers,        // fan-out: every user in the firm (broadcast)
        NotificationType type,
        String referenceType,
        UUID referenceId,
        String dedupKey,             // optional idempotency key
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
