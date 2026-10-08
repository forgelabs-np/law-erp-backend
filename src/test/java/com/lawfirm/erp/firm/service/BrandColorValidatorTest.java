package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BrandColorValidatorTest {

    @Test
    void normalizeHex_acceptsHashPrefixedMixedCase() {
        assertEquals("#0d47a1", BrandColorValidator.normalizeHex("#0D47A1"));
    }

    @Test
    void normalizeHex_acceptsBareHex() {
        assertEquals("#e3f2fd", BrandColorValidator.normalizeHex("E3F2FD"));
    }

    @Test
    void normalizeHex_trimsWhitespace() {
        assertEquals("#aabbcc", BrandColorValidator.normalizeHex("  aaBBcc  "));
    }

    @Test
    @DisplayName("Null and blank mean \"no color\", not an error")
    void normalizeHex_nullAndBlankReturnNull() {
        assertNull(BrandColorValidator.normalizeHex(null));
        assertNull(BrandColorValidator.normalizeHex("   "));
    }

    @Test
    void normalizeHex_rejectsShortHex() {
        assertThrows(BusinessRuleException.class, () -> BrandColorValidator.normalizeHex("#123"));
    }

    @Test
    void normalizeHex_rejectsNonHexCharacters() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> BrandColorValidator.normalizeHex("magenta"));
        assertTrue(ex.getMessage().contains("magenta"), "message should name the offending value");
    }

    @Test
    void normalizeHex_rejectsThreeDigitShorthand() {
        assertThrows(BusinessRuleException.class, () -> BrandColorValidator.normalizeHex("#fff"));
    }

    @Test
    void hasPersonalColors_trueWhenEitherColorIsSet() {
        assertTrue(BrandColorValidator.hasPersonalColors("#112233", null));
        assertTrue(BrandColorValidator.hasPersonalColors(null, "#445566"));
        assertTrue(BrandColorValidator.hasPersonalColors("#112233", "#445566"));
    }

    @Test
    void hasPersonalColors_falseWhenNeitherColorIsSet() {
        assertFalse(BrandColorValidator.hasPersonalColors(null, null));
        assertFalse(BrandColorValidator.hasPersonalColors("  ", ""));
    }
}
