package com.lawfirm.erp.modules.casemanagement.enums;

import java.util.EnumSet;
import java.util.Set;

public enum CaseStage {
    FILED(CaseType.CIVIL),
    UNDER_SUMMONS(CaseType.CIVIL),
    RESPONSE_PENDING(CaseType.CIVIL),
    MEDIATION(CaseType.CIVIL),
    EVIDENCE(CaseType.CIVIL),
    ARGUMENT(CaseType.CIVIL),
    JUDGMENT_AWAITED(CaseType.CIVIL),
    JUDGMENT_DELIVERED(CaseType.CIVIL),
    APPEAL(CaseType.CIVIL, CaseType.CRIMINAL),
    EXECUTION(CaseType.CIVIL),
    CLOSED(CaseType.CIVIL, CaseType.CRIMINAL),
    FIR_REGISTERED(CaseType.CRIMINAL),
    UNDER_INVESTIGATION(CaseType.CRIMINAL),
    CHARGE_SHEET_FILED(CaseType.CRIMINAL),
    PLEA(CaseType.CRIMINAL),
    TRIAL(CaseType.CRIMINAL),
    SENTENCING(CaseType.CRIMINAL);

    private final Set<CaseType> validFor;

    CaseStage(CaseType first, CaseType... rest) {
        this.validFor = EnumSet.of(first, rest);
    }

    public boolean isValidFor(CaseType caseType) {
        return validFor.contains(caseType);
    }

    public static CaseStage initial(CaseType caseType) {
        return switch (caseType) {
            case CIVIL -> FILED;
            case CRIMINAL -> FIR_REGISTERED;
        };
    }

    private static final Set<CaseStage> CIVIL_TRANSITIONS = Set.of(
            UNDER_SUMMONS, RESPONSE_PENDING, MEDIATION, EVIDENCE, ARGUMENT,
            JUDGMENT_AWAITED, JUDGMENT_DELIVERED, APPEAL, EXECUTION, CLOSED);

    private static final Set<CaseStage> CRIMINAL_TRANSITIONS = Set.of(
            UNDER_INVESTIGATION, CHARGE_SHEET_FILED, PLEA, TRIAL,
            JUDGMENT_AWAITED, JUDGMENT_DELIVERED, SENTENCING, APPEAL, CLOSED);

    public Set<CaseStage> allowedTransitions() {
        return switch (this) {
            case FILED -> Set.of(UNDER_SUMMONS, CLOSED);
            case UNDER_SUMMONS -> Set.of(RESPONSE_PENDING, CLOSED);
            case RESPONSE_PENDING -> Set.of(MEDIATION, EVIDENCE, CLOSED);
            case MEDIATION -> Set.of(EVIDENCE, CLOSED);
            case EVIDENCE -> Set.of(ARGUMENT, CLOSED);
            case ARGUMENT -> Set.of(JUDGMENT_AWAITED, CLOSED);
            case JUDGMENT_AWAITED -> Set.of(JUDGMENT_DELIVERED, CLOSED);
            case JUDGMENT_DELIVERED -> Set.of(APPEAL, EXECUTION, SENTENCING, CLOSED);
            case APPEAL -> Set.of(CLOSED);
            case EXECUTION -> Set.of(CLOSED);
            case FIR_REGISTERED -> Set.of(UNDER_INVESTIGATION, CLOSED);
            case UNDER_INVESTIGATION -> Set.of(CHARGE_SHEET_FILED, CLOSED);
            case CHARGE_SHEET_FILED -> Set.of(PLEA, TRIAL, CLOSED);
            case PLEA -> Set.of(TRIAL, CLOSED);
            case TRIAL -> Set.of(JUDGMENT_AWAITED, CLOSED);
            case SENTENCING -> Set.of(APPEAL, CLOSED);
            case CLOSED -> Set.of(); // terminal
        };
    }
}
