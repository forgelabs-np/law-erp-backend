package com.lawfirm.erp.modules.casemanagement.dto;

import com.lawfirm.erp.modules.casemanagement.dto.request.PartyEntryRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.PartyRoleRequest;
import com.lawfirm.erp.modules.casemanagement.enums.PartyType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the party-validation fix: roleType is an enum, so it
 * must be validated with @NotNull — @NotBlank on an enum throws HV000030
 * ("not applicable to PartyType") and made adding parties to a matter always
 * fail.
 */
class PartyEntryRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("Valid PartyEntryRequest passes validation")
    void validRequest_passes() {
        PartyEntryRequest request = new PartyEntryRequest();
        request.setFullName("Ram Sharma");
        request.setRoleType(PartyType.PLAINTIFF);

        Set<ConstraintViolation<PartyEntryRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Null roleType → 'Party role type is required' (no HV000030 crash)")
    void nullRoleType_failsCleanly() {
        PartyEntryRequest request = new PartyEntryRequest();
        request.setFullName("Ram Sharma");

        Set<ConstraintViolation<PartyEntryRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("Party role type is required", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Blank fullName still fails validation")
    void blankFullName_fails() {
        PartyEntryRequest request = new PartyEntryRequest();
        request.setFullName("   ");
        request.setRoleType(PartyType.DEFENDANT);

        Set<ConstraintViolation<PartyEntryRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("Party full name is required", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("PartyRoleRequest roleType is validated with @NotNull too")
    void partyRoleRequest_nullRoleType_failsCleanly() {
        PartyRoleRequest request = new PartyRoleRequest();
        request.setFullName("Sita Gurung");

        Set<ConstraintViolation<PartyRoleRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("Role type is required", violations.iterator().next().getMessage());
    }
}
