package com.lawfirm.erp.common.enums;

public enum PermissionAction {
    // Basic CRUD operations
    VIEW, CREATE, EDIT, DELETE,

    // Module level access
    ACCESS,

    // Document operations
    UPLOAD, DOWNLOAD, SHARE,

    // Export operations
    EXPORT,

    // Case management operations
    SCHEDULE, UPDATE_STATUS, ASSIGN,

    // Approval operations
    APPROVE, REJECT, REVIEW,

    // Additional operations
    ARCHIVE, RESTORE, PRINT
}