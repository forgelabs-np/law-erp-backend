package com.lawfirm.erp.common.enums;

/**
 * Entity types that can be audited.
 * Each name must be <= 20 chars — stored as CHAR(20) in DB.
 */
public enum AuditEntity {
    USER,
    CLIENT,
    FIRM,
    FIRM_MODULE,
    ROLE,
    ROLE_PERMISSION,
    PERMISSION,
    MODULE,
    CASE,
    DOCUMENT,
    INVOICE,
    PAYMENT,
    HEARING,
    DEPARTMENT,
    AUTH,
    EMAIL_CONFIG,
    SYSTEM_CONFIG
}