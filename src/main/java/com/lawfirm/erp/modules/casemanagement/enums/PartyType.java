package com.lawfirm.erp.modules.casemanagement.enums;

/**
 * A party's role within a specific CourtCase instance.
 * Roles are per-instance (CourtCaseRole) because they flip on appeal —
 * e.g. a District Court DEFENDANT becomes the APPELLANT at High Court.
 */
public enum PartyType {
    PLAINTIFF,
    DEFENDANT,
    ACCUSED,
    APPELLANT,
    RESPONDENT,
    APPLICANT,
    /** The state/prosecution side in a criminal matter (or the private complainant). */
    COMPLAINANT,
    /** The person who lodged the FIR — distinct from the state as prosecuting party. */
    INFORMANT
}
