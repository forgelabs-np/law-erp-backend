package com.lawfirm.erp.modules.scraper.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class NepaliDateUtilTest {

    @Test
    @DisplayName("BS to AD matches known reference dates")
    void bsToAdReferences() {
        assertEquals(LocalDate.of(2024, 4, 13), NepaliDateUtil.bsToAd("2081-01-01"));
        assertEquals(LocalDate.of(2025, 4, 14), NepaliDateUtil.bsToAd("2082-01-01"));
        assertEquals(LocalDate.of(2026, 8, 17), NepaliDateUtil.bsToAd("2083-05-01"));
    }

    @Test
    @DisplayName("AD to BS matches known reference dates")
    void adToBsReferences() {
        assertEquals("2081-01-01", NepaliDateUtil.adToBs(LocalDate.of(2024, 4, 13)));
        assertEquals("2083-05-01", NepaliDateUtil.adToBs(LocalDate.of(2026, 8, 17)));
    }

    @Test
    @DisplayName("Round-trips across a range of dates")
    void roundTrip() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 400; i++) {
            LocalDate ad = start.plusDays(i);
            String bs = NepaliDateUtil.adToBs(ad);
            assertNotNull(bs, "adToBs failed for " + ad);
            assertEquals(ad, NepaliDateUtil.bsToAd(bs), "round trip failed for " + ad);
        }
    }

    @Test
    @DisplayName("Rejects out-of-range and malformed dates")
    void invalid() {
        assertNull(NepaliDateUtil.bsToAd("1999-01-01")); // before 2000 table
        assertNull(NepaliDateUtil.bsToAd("2091-01-01")); // after 2090 table
        assertNull(NepaliDateUtil.bsToAd("2083-13-01")); // bad month
        assertNull(NepaliDateUtil.bsToAd("2083-02-40")); // bad day
        assertNull(NepaliDateUtil.bsToAd("not-a-date"));
        assertNull(NepaliDateUtil.bsToAd(null));
    }
}
