package com.lawfirm.erp.modules.document.enums;

/** Lifecycle of a document row. {@code ARCHIVED} is the only soft-delete state. */
public enum DocumentStatus {
    PENDING_UPLOAD,
    ACTIVE,
    ARCHIVED
}
