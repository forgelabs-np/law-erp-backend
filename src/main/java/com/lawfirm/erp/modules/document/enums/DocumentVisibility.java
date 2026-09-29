package com.lawfirm.erp.modules.document.enums;

/**
 * Who may read a document. Uploads start {@code PRIVATE}; a user holding
 * {@code DOCUMENT_MANAGEMENT:SHARE} promotes one to {@code SHARED}, which is the only state a
 * client account can see.
 */
public enum DocumentVisibility {
    PRIVATE,
    SHARED
}
