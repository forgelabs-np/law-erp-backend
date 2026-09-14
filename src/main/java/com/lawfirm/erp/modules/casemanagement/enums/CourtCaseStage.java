package com.lawfirm.erp.modules.casemanagement.enums;

import java.util.Set;

/**
 * v2 stage machine — keyed by (court level x matter type x relation type).
 *
 * Trial-level (District) civil and criminal have their own lifecycles.
 * Appellate-level (High/Supreme/Specialized) procedure is shared by both types.
 * Writ petitions (filed directly at HC/SC) have their own short lifecycle.
 *
 * HEARING_STAGE is deliberately one wide bucket: the granular Tarik/Peshi
 * progress lives in the CourtEvent stream, not duplicated here.
 */
public enum CourtCaseStage {

    // Trial-level civil (District)
    FILED,
    SUMMONS_ISSUED,
    RESPONSE_PENDING,
    MEDIATION,
    HEARING_STAGE,
    JUDGMENT_AWAITED,
    JUDGMENT_DELIVERED,
    APPEALED,
    EXECUTION,
    SENTENCING,
    CLOSED,

    // Trial-level criminal (District)
    FIR_REGISTERED,
    UNDER_INVESTIGATION,
    CHARGE_SHEET_FILED,

    // Appellate-level (High / Supreme / Specialized)
    APPEAL_FILED,
    ADMITTED,
    NOTICE_ISSUED,
    FURTHER_APPEALED,
    REMANDED,

    // Writ (direct HC/SC filing)
    WRIT_FILED,
    ORDER_ISSUED;

    public boolean isValidFor(CourtLevel level, MatterType matterType, RelationType relationType) {
        if (relationType == RelationType.WRIT) {
            return level != CourtLevel.DISTRICT && isWritStage(this);
        }
        return switch (level) {
            case DISTRICT -> matterType == MatterType.CIVIL ? isTrialCivil(this) : isTrialCriminal(this);
            case HIGH, SUPREME, SPECIALIZED -> isAppellate(this);
        };
    }

    private static boolean isTrialCivil(CourtCaseStage s) {
        return s == FILED || s == SUMMONS_ISSUED || s == RESPONSE_PENDING || s == MEDIATION
                || s == HEARING_STAGE || s == JUDGMENT_AWAITED || s == JUDGMENT_DELIVERED
                || s == APPEALED || s == EXECUTION || s == CLOSED;
    }

    private static boolean isTrialCriminal(CourtCaseStage s) {
        return s == FIR_REGISTERED || s == UNDER_INVESTIGATION || s == CHARGE_SHEET_FILED
                || s == HEARING_STAGE || s == JUDGMENT_AWAITED || s == JUDGMENT_DELIVERED
                || s == SENTENCING || s == APPEALED || s == EXECUTION || s == CLOSED;
    }

    private static boolean isAppellate(CourtCaseStage s) {
        return s == APPEAL_FILED || s == ADMITTED || s == NOTICE_ISSUED || s == HEARING_STAGE
                || s == JUDGMENT_AWAITED || s == JUDGMENT_DELIVERED || s == FURTHER_APPEALED
                || s == REMANDED || s == EXECUTION || s == CLOSED;
    }

    private static boolean isWritStage(CourtCaseStage s) {
        return s == WRIT_FILED || s == ADMITTED || s == HEARING_STAGE
                || s == ORDER_ISSUED || s == CLOSED;
    }

    public static CourtCaseStage initialFor(CourtLevel level, MatterType matterType, RelationType relationType) {
        if (relationType == RelationType.WRIT) return WRIT_FILED;
        if (level == CourtLevel.DISTRICT) {
            return matterType == MatterType.CIVIL ? FILED : FIR_REGISTERED;
        }
        return APPEAL_FILED;
    }

    public Set<CourtCaseStage> allowedTransitions() {
        return switch (this) {
            case FILED -> Set.of(SUMMONS_ISSUED, CLOSED);
            case SUMMONS_ISSUED -> Set.of(RESPONSE_PENDING, CLOSED);
            case RESPONSE_PENDING -> Set.of(MEDIATION, HEARING_STAGE, CLOSED);
            case MEDIATION -> Set.of(HEARING_STAGE, CLOSED);
            case FIR_REGISTERED -> Set.of(UNDER_INVESTIGATION, CLOSED);
            case UNDER_INVESTIGATION -> Set.of(CHARGE_SHEET_FILED, CLOSED);
            case CHARGE_SHEET_FILED -> Set.of(HEARING_STAGE, CLOSED);
            case HEARING_STAGE -> Set.of(JUDGMENT_AWAITED, CLOSED);
            case APPEAL_FILED -> Set.of(ADMITTED, NOTICE_ISSUED, CLOSED);
            case ADMITTED -> Set.of(HEARING_STAGE, CLOSED);
            case NOTICE_ISSUED -> Set.of(HEARING_STAGE, CLOSED);
            case WRIT_FILED -> Set.of(ADMITTED, CLOSED);
            case JUDGMENT_AWAITED -> Set.of(JUDGMENT_DELIVERED);
            case JUDGMENT_DELIVERED -> Set.of(APPEALED, FURTHER_APPEALED, REMANDED, SENTENCING, EXECUTION, CLOSED);
            case SENTENCING -> Set.of(APPEALED, EXECUTION, CLOSED);
            case APPEALED -> Set.of(CLOSED);
            case FURTHER_APPEALED -> Set.of(CLOSED);
            case REMANDED -> Set.of(CLOSED);
            case EXECUTION -> Set.of(CLOSED);
            case ORDER_ISSUED -> Set.of(CLOSED);
            case CLOSED -> Set.of(); // terminal
        };
    }

    /**
     * Stages that mean "this CourtCase has been superseded by a child" (appeal/remand filed).
     */
    public static boolean isSuperseded(CourtCaseStage s) {
        return s == APPEALED || s == FURTHER_APPEALED || s == REMANDED;
    }

    /** Stages that close the door on further movement. */
    public static boolean isTerminal(CourtCaseStage s) {
        return s == CLOSED;
    }
}
