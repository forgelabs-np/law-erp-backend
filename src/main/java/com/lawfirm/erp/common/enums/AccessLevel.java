package com.lawfirm.erp.common.enums;

public enum AccessLevel {
    NO_ACCESS,      // No access to this module
    READ_ONLY,      // Only VIEW permission
    FULL,           // All standard permissions (VIEW, CREATE, EDIT, DELETE)
    CUSTOM          // Manually selected permissions
}