package com.lawfirm.erp.modules.scraper.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DevanagariConverterTest {

    @Test
    @DisplayName("Converts Devanagari digits to Arabic")
    void toArabic() {
        assertEquals("2083-05-01", DevanagariConverter.toArabic("२०८३-०५-०१"));
        assertEquals("081-C4-3827", DevanagariConverter.toArabic("०८१-C४-३८२७"));
        assertEquals("39-081-32030", DevanagariConverter.toArabic("३९-०८१-३२०३०"));
    }

    @Test
    @DisplayName("Converts Arabic digits to Devanagari")
    void toDevanagari() {
        assertEquals("२०८३-०५-०१", DevanagariConverter.toDevanagari("2083-05-01"));
        assertEquals("०८१-C४-३८२७", DevanagariConverter.toDevanagari("081-C4-3827"));
    }

    @Test
    @DisplayName("Leaves non-digit characters untouched and handles empty input")
    void passthrough() {
        assertEquals("", DevanagariConverter.toArabic(""));
        assertNull(DevanagariConverter.toArabic(null));
        assertEquals("जालसाजी", DevanagariConverter.toArabic("जालसाजी"));
        assertFalse(DevanagariConverter.containsDevanagariDigit("जालसाजी"));
        assertTrue(DevanagariConverter.containsDevanagariDigit("०८१-C४"));
    }
}
