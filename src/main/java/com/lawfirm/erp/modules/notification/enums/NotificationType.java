package com.lawfirm.erp.modules.notification.enums;

/**
 * Concrete notification kinds. Each type pins its category so producers
 * never pass category separately and the bell icon can group by category.
 */
public enum NotificationType {

    /**
     * Work handed to a person. The e-mail is part of the assignment rather than a
     * nicety — an assignment the assignee never hears about is a case nobody works.
     */
    CASE_ASSIGNED(NotificationCategory.SYSTEM, true),

    INVOICE_STATUS(NotificationCategory.SYSTEM, false),
    APPEAL_LAPSED(NotificationCategory.SYSTEM, false),

    // ── ALERT — time-sensitive, in-app + email by default, opt-out locked ──
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

    /**
     * Whether a recipient with no stored preference gets this type by e-mail.
     *
     * <p>ALERT types are always in (their opt-out is refused outright) and a single routine
     * SYSTEM type is too: {@link #CASE_ASSIGNED}, so an assignment e-mails the person it was
     * given to. Unlike ALERT it stays overridable — the preference screen shows it as on and a
     * user may still silence it.
     */
    public boolean emailsByDefault() {
        return emailsByDefault || category == NotificationCategory.ALERT;
    }
}
