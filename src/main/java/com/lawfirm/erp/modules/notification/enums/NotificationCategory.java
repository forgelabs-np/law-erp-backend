package com.lawfirm.erp.modules.notification.enums;

/**
 * Coarse bucket driving bell-icon grouping and (future) channel defaults.
 * Types carry their category — see NotificationType.
 */
public enum NotificationCategory {
    /** Routine in-app updates: assignments, status changes. */
    SYSTEM,
    /** Time-sensitive items requiring user attention (hearing/appeal deadlines). */
    ALERT,
    /** Firm-wide announcements sent by FIRM_ADMIN. */
    BROADCAST
}
