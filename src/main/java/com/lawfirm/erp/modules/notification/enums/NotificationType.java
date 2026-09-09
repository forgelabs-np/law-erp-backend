package com.lawfirm.erp.modules.notification.enums;

/**
 * Concrete notification kinds. Each type pins its category so producers
 * never pass category separately and the bell icon can group by category.
 */
public enum NotificationType {

    CASE_ASSIGNED(NotificationCategory.SYSTEM),
    INVOICE_STATUS(NotificationCategory.SYSTEM),
    APPEAL_LAPSED(NotificationCategory.SYSTEM),

    // ── ALERT — time-sensitive, in-app + email by default, opt-out locked ──
    HEARING_REMINDER(NotificationCategory.ALERT),
    APPEAL_DEADLINE(NotificationCategory.ALERT),

    ANNOUNCEMENT(NotificationCategory.BROADCAST);

    private final NotificationCategory category;

    NotificationType(NotificationCategory category) {
        this.category = category;
    }

    public NotificationCategory getCategory() {
        return category;
    }
}
