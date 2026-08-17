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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DailyTableParserTest {

    private DailyTableParser parser;

    @BeforeEach
    void setUp() {
        parser = new DailyTableParser();
    }

    private String fixture() throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("fixtures/scraper/daily_kathmandu_39.html")) {
            assertNotNull(in, "fixture missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Parses the real daily page: judge names, page date, normalized numbers")
    void parsesRealDailyPage() throws IOException {
        List<HearingRecord> records = parser.parse(fixture(), 39);

        assertFalse(records.isEmpty());
        assertTrue(records.size() > 50, "real daily list should have many rows, got " + records.size());

        for (HearingRecord r : records) {
            assertEquals(39, r.getCourtId());
            assertEquals(HearingSource.DAILY, r.getSource());
            assertEquals("2083-05-01", r.getHearingDateBs(), "page date must be extracted");
            assertEquals(LocalDate.of(2026, 8, 17), r.getHearingDateAd());
            assertNotNull(r.getCaseNoInternal(), "every row must carry the internal case number");
            assertNotNull(r.getJudgeName(), "every row must be assigned to a judge section");
        }

        // The summary judge-count table must not leak in as a row.
        assertTrue(records.stream().noneMatch(r -> "माननीय न्यायाधीश".equals(r.getPlaintiff())));

        // Judge names come from the bench headers — several distinct judges.
        Set<String> judges = records.stream().map(HearingRecord::getJudgeName).collect(Collectors.toSet());
        assertTrue(judges.size() > 5, "expected many judges, got " + judges.size());
        assertTrue(judges.contains("माननीय जिल्ला न्यायाधीश श्री अशोककुमार बस्नेत"));
    }

    @Test
    @DisplayName("Extracts both case numbers, parties and the outcome column")
    void extractsColumns() throws IOException {
        List<HearingRecord> records = parser.parse(fixture(), 39);

        HearingRecord first = records.get(0);
        assertEquals("081-C1-7530", first.getCaseNoBs());
        assertEquals("39-081-39885", first.getCaseNoInternal());
        assertEquals("माननीय जिल्ला न्यायाधीश श्री अशोककुमार बस्नेत", first.getJudgeName());
        assertTrue(first.getPlaintiff().startsWith("रुपेश पराजुली"));
        assertNull(first.getOrderType(), "this row has an empty outcome cell");

        // A row with a स्थगित (adjourned) outcome.
        HearingRecord st = records.stream()
                .filter(r -> "39-082-09328".equals(r.getCaseNoInternal()))
                .findFirst().orElseThrow();
        assertTrue(st.getOrderType().startsWith("स्थगित"));

        // A row with an आदेश (order) outcome.
        HearingRecord ad = records.stream()
                .filter(r -> "39-082-12228".equals(r.getCaseNoInternal()))
                .findFirst().orElseThrow();
        assertTrue(ad.getOrderType().startsWith("आदेश"));

        // A row with a फैसला (judgment) outcome.
        HearingRecord fj = records.stream()
                .filter(r -> "39-083-03909".equals(r.getCaseNoInternal()))
                .findFirst().orElseThrow();
        assertTrue(fj.getOrderType().contains("फैसला"));
    }

    @Test
    @DisplayName("Returns empty for blank HTML")
    void blankHtml() {
        assertTrue(parser.parse("", 39).isEmpty());
        assertTrue(parser.parse(null, 39).isEmpty());
    }
}
