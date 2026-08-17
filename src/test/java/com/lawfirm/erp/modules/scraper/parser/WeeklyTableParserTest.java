package com.lawfirm.erp.modules.scraper.parser;

import com.lawfirm.erp.modules.scraper.dto.HearingRecord;
import com.lawfirm.erp.modules.scraper.enums.HearingSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeeklyTableParserTest {

    private WeeklyTableParser parser;

    @BeforeEach
    void setUp() {
        parser = new WeeklyTableParser();
    }

    private String fixture() throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("fixtures/scraper/weekly_kathmandu_39.html")) {
            assertNotNull(in, "fixture missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Parses the real weekly cause list into normalized records")
    void parsesRealWeeklyPage() throws IOException {
        List<HearingRecord> records = parser.parse(fixture(), 39);

        assertFalse(records.isEmpty(), "should parse rows");
        assertTrue(records.size() > 10, "fixture should contain many rows, got " + records.size());

        for (HearingRecord r : records) {
            assertEquals(39, r.getCourtId());
            assertEquals("2083-05-01", r.getHearingDateBs());
            assertEquals(LocalDate.of(2026, 8, 17), r.getHearingDateAd());
            assertEquals(HearingSource.WEEKLY, r.getSource());
            assertNotNull(r.getCaseNoInternal(), "internal case number must be extracted");
            assertNull(r.getOrderType(), "weekly feed has no outcome column");
            assertNull(r.getJudgeName(), "weekly feed has no per-judge grouping");
        }
    }

    @Test
    @DisplayName("Extracts both case-number forms and splits the combined party column")
    void extractsCaseNumbersAndParties() throws IOException {
        List<HearingRecord> records = parser.parse(fixture(), 39);

        HearingRecord first = records.get(0);
        assertEquals("082-CP-1835", first.getCaseNoBs());
        assertEquals("39-082-06709", first.getCaseNoInternal());
        assertEquals("अंशचलन", first.getSubject());
        assertEquals("सुजा अधिकारी भन्ने सोभा अधिकारीको संरक्षक सुनिता अधिकारी", first.getPlaintiff());
        assertEquals("सुरज अधिकारी", first.getDefendant());
    }

    @Test
    @DisplayName("Returns empty for blank HTML")
    void blankHtml() {
        assertTrue(parser.parse("", 39).isEmpty());
        assertTrue(parser.parse("  ", 39).isEmpty());
        assertTrue(parser.parse(null, 39).isEmpty());
    }
}
