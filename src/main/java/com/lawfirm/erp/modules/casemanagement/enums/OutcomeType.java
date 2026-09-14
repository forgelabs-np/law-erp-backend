package com.lawfirm.erp.modules.casemanagement.enums;

/**
 * What actually happened at a held Peshi/Tarik — drives what happens next.
 */
public enum OutcomeType {
    PART_HEARD,           // peshi continues next time — no progress boundary
    ARGUMENTS_COMPLETE,
    EVIDENCE_TAKEN,
    ADJOURNED_NO_PROGRESS,
    ORDER_PASSED,
    ORDER_ISSUED,
    STAY_GRANTED,
    INTERIM_ORDER,
    JUDGMENT_DELIVERED,
    WITHDRAWN
}
