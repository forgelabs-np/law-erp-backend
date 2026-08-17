package com.lawfirm.erp.modules.scraper.parser;

import com.lawfirm.erp.modules.scraper.dto.CaseDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CaseDetailParserTest {

    private CaseDetailParser parser;

    @BeforeEach
    void setUp() {
        parser = new CaseDetailParser();
    }

    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("fixtures/scraper/" + name)) {
            assertNotNull(in, "fixture missing: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Parses the live case_process_detail page: numbers, info, parties, hearing history")
    void parsesRealDetailPage() throws IOException {
        CaseDetailResponse d = parser.parse(fixture("case_detail.html"), 39);

        assertTrue(d.isFound());
        assertEquals(39, d.getCourtId());
        assertEquals("081-C1-7530", d.getCaseNoBs());
        assertEquals("39-081-39885", d.getCaseNoInternal());
        assertEquals("2082-02-19", d.getRegistrationDate());
        assertTrue(d.getCaseType().replace("\u200C", "").startsWith("बैङकिङ्ग कसूर"));
        assertTrue(d.getSubject().replace("\u200C", "").startsWith("बैङकिङ्ग कसूर"));
        assertEquals("मुद्दा फाटँ 1 (सरल)", d.getDivision());
        assertEquals("7", d.getHearingCount());
        assertEquals("चालु", d.getStatus());

        assertFalse(d.getPlaintiffs().isEmpty());
        assertTrue(d.getPlaintiffs().get(0).getName().startsWith("रुपेश पराजुली"));
        assertFalse(d.getDefendants().isEmpty());
        assertTrue(d.getDefendants().get(0).getName().startsWith("शान्ति कुमारी खत्री"));

        // तारेख विवरण — dates + type only.
        assertFalse(d.getTarekhe().isEmpty());
        assertEquals("2083-05-01", d.getTarekhe().get(0).getDateBs());

        // पेशी विवरण — the full hearing history with judge + order.
        assertFalse(d.getHearings().isEmpty());
        CaseDetailResponse.HearingEntry first = d.getHearings().get(0);
        assertEquals("2083-05-01", first.getDateBs());
        assertEquals("पूरक", first.getType());
        assertEquals("मुद्दा फाटँ 1 (सरल)", first.getDivision());
        assertTrue(first.getJudge().startsWith("श्री अशोककुमार"));
        assertTrue(first.getOrder().startsWith("आदेश"));
    }

    @Test
    @DisplayName("Returns found=false when the site reports no record")
    void notFound() throws IOException {
        CaseDetailResponse d = parser.parse(fixture("case_detail_notfound.html"), 39);
        assertFalse(d.isFound());
        assertTrue(d.getHearings().isEmpty());
    }
}
