package com.lawfirm.erp.common.enums;

/**
 * All auditable actions across the system.
 * Each name must be <= 30 chars — stored as CHAR(30) in DB.
 */
public enum AuditAction {

    // ── Auth ────────────────────────────────
    LOGIN,
    LOGOUT,
    LOGIN_FAILED,
    PASSWORD_CHANGED,
    TOKEN_REFRESHED,
    MFA_ENABLED,

    // ── User / Employee ──────────────────────
    USER_CREATED,
    USER_UPDATED,
    USER_DEACTIVATED,
    USER_ACTIVATED,
    USER_ROLE_CHANGED,
    USER_BLOCKED,
    USER_UNBLOCKED,

    // ── Client ──────────────────────────────
    CLIENT_CREATED,
    CLIENT_UPDATED,
    CLIENT_PORTAL_ENABLED,
    CLIENT_PORTAL_DISABLED,

    // ── Case management (Phase 9) ────────────
    CASE_CREATED,
    CASE_UPDATED,
    CASE_DELETED,
    CASE_ASSIGNED,
    CASE_STATUS_CHANGED,
    CASE_ARCHIVED,
    CASE_REOPENED,

    // ── Matter / Court case / Court event (Phase 10) ──
    MATTER_CREATED,
    MATTER_UPDATED,
    COURT_CASE_CREATED,
    COURT_CASE_UPDATED,
    COURT_CASE_STAGE_CHANGED,
    COURT_CASE_CLOSED,
    APPEAL_FILED,
    JUDGMENT_RECORDED,
    COURT_EVENT_SCHEDULED,
    COURT_EVENT_UPDATED,
    COURT_EVENT_HELD,
    COURT_EVENT_ADJOURNED,
    COURT_EVENT_CANCELLED,

    // ── Document management ──────────────────
    DOCUMENT_UPLOADED,
    DOCUMENT_DELETED,
    DOCUMENT_DOWNLOADED,
    DOCUMENT_SHARED,

    // ── Billing ──────────────────────────────
    INVOICE_CREATED,
    INVOICE_UPDATED,
    INVOICE_DELETED,
    INVOICE_APPROVED,
    INVOICE_SENT,
    PAYMENT_RECORDED,

    // ── Calendar ─────────────────────────────
    HEARING_SCHEDULED,
    HEARING_UPDATED,
    HEARING_CANCELLED,

    // ── Firm management ──────────────────────
    FIRM_CREATED,
    FIRM_UPDATED,
    FIRM_SUSPENDED,
    FIRM_MODULE_ENABLED,
    FIRM_MODULE_DISABLED,
    FIRM_MODULE_CONFIGURED,

    // ── RBAC ─────────────────────────────────
    ROLE_ASSIGNED,
    ROLE_UPDATED,
    ROLE_CREATED,
    ROLE_DELETED,
    ROLE_ACTIVATED,
    ROLE_DEACTIVATED,
    ROLE_PERMISSION_CHANGED,

    PERMISSION_CREATED,
    PERMISSION_DELETED,
    PERMISSION_ACTIVATED,
    PERMISSION_DEACTIVATED,
    PERMISSION_UPDATED,
    MODULE_CREATED,
    MODULE_ACTIVATED,
    MODULE_DEACTIVATED,
    MODULE_UPDATED,
    MODULE_DELETED,

    // ── Email / config (Phase 10) ──────────────
    EMAIL_SENT,
    EMAIL_FAILED,
    EMAIL_CONFIG_UPDATED,
    EMAIL_CONFIG_TESTED,
    EMAIL_CONFIG_DELETED,
    CONFIG_UPDATED,
    CONFIG_DELETED,

    // ── Project management ───────────────────
    PROJECT_CREATED,
    PROJECT_UPDATED,
    PROJECT_COMPLETED,
    CREDENTIAL_ADDED,
    CREDENTIAL_REVEALED,
    RENEWAL_CREATED,
    RENEWAL_COMPLETED
}