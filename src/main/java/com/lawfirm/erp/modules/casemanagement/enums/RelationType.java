package com.lawfirm.erp.modules.casemanagement.enums;

/**
 * How a CourtCase relates to its parent (or the matter when it is the first).
 */
public enum RelationType {
    ORIGINAL,
    APPEAL,
    CROSS_APPEAL,
    REMAND,
    REVISION,
    WRIT,
    REVIEW
}
