package com.lawfirm.erp.common.constant;

public final class NotificationConstants {

    private NotificationConstants() {}

    // ── Controller @Operation strings ─────────────────────────────────────
    public static final String LIST_NOTIFICATIONS_SUMMARY = "List my notifications";
    public static final String LIST_NOTIFICATIONS_DESCRIPTION = "Returns the current user's notifications, newest first. Set unreadOnly=true to fetch only unread ones.";
    public static final String UNREAD_COUNT_SUMMARY = "Get unread notification count";
    public static final String UNREAD_COUNT_DESCRIPTION = "Lightweight endpoint for the bell-icon badge — poll every 45-60s.";
    public static final String MARK_READ_SUMMARY = "Mark one notification as read";
    public static final String MARK_ALL_READ_SUMMARY = "Mark all my notifications as read";
    public static final String SEND_BROADCAST_SUMMARY = "Send a firm announcement";
    public static final String SEND_BROADCAST_DESCRIPTION = "Firm-admin only. Delivers an ANNOUNCEMENT to ALL firm users or to one role code (e.g. ADVOCATE). Audience must be ALL or a valid role code.";
    public static final String GET_PREFERENCES_SUMMARY = "List my notification preferences";
    public static final String GET_PREFERENCES_DESCRIPTION = "Every notification type with its effective email-channel setting. ALERT types are locked on — deadlines must not be silencable.";
    public static final String UPSERT_PREFERENCE_SUMMARY = "Update one notification preference";

    // ── Renderer copy ─────────────────────────────────────────────────────
    public static final String CASE_ASSIGNED_TITLE = "Assigned to matter";
    public static final String CASE_ASSIGNED_BODY = "You were assigned to matter %s as %s.";
    public static final String INVOICE_STATUS_TITLE = "Invoice %s";
    public static final String INVOICE_STATUS_BODY = "Invoice %s status changed to %s.";
    public static final String HEARING_REMINDER_TITLE = "Hearing tomorrow";
    public static final String HEARING_REMINDER_BODY = "Hearing for matter %s at %s — %s.";
    public static final String APPEAL_DEADLINE_TITLE = "Appeal deadline approaching";
    public static final String APPEAL_DEADLINE_BODY = "Appeal deadline for matter %s (%s) is %s — %s day(s) remaining.";
    public static final String APPEAL_LAPSED_TITLE = "Appeal window lapsed";
    public static final String APPEAL_LAPSED_BODY = "No appeal was filed for matter %s (%s) before the deadline — the judgment is now final.";
    public static final String ANNOUNCEMENT_TITLE = "%s";
    public static final String ANNOUNCEMENT_BODY = "%s";

    // ── Trial ─────────────────────────────────────────────────────────
    public static final String TRIAL_EXPIRING_TITLE = "Trial period expiring soon";
    public static final String TRIAL_EXPIRING_BODY = "Your trial for %s expires in %d day(s). Please contact support to continue after the trial ends.";
    public static final String TRIAL_EXPIRED_TITLE = "Trial period expired";
    public static final String TRIAL_EXPIRED_BODY = "Your trial for %s has expired. Please contact support to regain access.";
}
