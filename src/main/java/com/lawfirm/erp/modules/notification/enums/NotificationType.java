package com.lawfirm.erp.modules.notification.enums;

public enum NotificationType {

    CASE_ASSIGNED(NotificationCategory.SYSTEM, true),

    INVOICE_STATUS(NotificationCategory.SYSTEM, false),
    APPEAL_LAPSED(NotificationCategory.SYSTEM, false),

    HEARING_REMINDER(NotificationCategory.ALERT, true),
    APPEAL_DEADLINE(NotificationCategory.ALERT, true),

    TRIAL_EXPIRING(NotificationCategory.ALERT, true),
    TRIAL_EXPIRED(NotificationCategory.SYSTEM, false),

    ANNOUNCEMENT(NotificationCategory.BROADCAST, false);

    private final NotificationCategory category;
    private final boolean emailsByDefault;

    NotificationType(NotificationCategory category, boolean emailsByDefault) {
        this.category = category;
        this.emailsByDefault = emailsByDefault;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public boolean emailsByDefault() {
        return emailsByDefault || category == NotificationCategory.ALERT;
    }
}
