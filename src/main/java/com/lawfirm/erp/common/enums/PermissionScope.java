package com.lawfirm.erp.common.enums;

public enum PermissionScope {
    GLOBAL,     // Across all firms (Super Admin only)
    TENANT,     // All records in the firm
    ASSIGNED,   // Records assigned to the user
    OWN         // User's own records (clients)
}