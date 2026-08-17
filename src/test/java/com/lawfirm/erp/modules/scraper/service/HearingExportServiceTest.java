package com.lawfirm.erp.modules.scraper.service;

import com.lawfirm.erp.modules.scraper.config.ScraperProperties;
import com.lawfirm.erp.modules.scraper.entity.DailyHearing;
import com.lawfirm.erp.modules.scraper.entity.HearingMatch;
import com.lawfirm.erp.modules.scraper.entity.WeeklyHearing;
import com.lawfirm.erp.modules.scraper.repository.DailyHearingRepository;
import com.lawfirm.erp.modules.scraper.repository.HearingMatchRepository;
import com.lawfirm.erp.modules.scraper.repository.WeeklyHearingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HearingExportServiceTest {

    @Mock private DailyHearingRepository dailyRepository;
    @Mock private WeeklyHearingRepository weeklyRepository;
    @Mock private HearingMatchRepository matchRepository;

    @TempDir
    Path tempDir;

    private HearingExportService service;

    @BeforeEach
    void setUp() {
        ScraperProperties props = new ScraperProperties();
        props.setExportDir(tempDir.toString());
        service = new HearingExportService(dailyRepository, weeklyRepository, matchRepository, props);
    }

    private DailyHearing daily(int courtId, LocalDate ad, String internal, String subject) {
        DailyHearing h = new DailyHearing();
        h.setCourtId(courtId);
        h.setHearingDateBs("2083-05-01");
        h.setHearingDateAd(ad);
        h.setCaseNoBs("081-C1-7530");
        h.setCaseNoInternal(internal);
        h.setJudgeName("माननीय जिल्ला न्यायाधीश श्री अशोककुमार बस्नेत");
        h.setSubject(subject);
        h.setPlaintiff("रुपेश पराजुली को जाहेरीले नेपाल सरकार");
        h.setDefendant("शान्ति कुमारी खत्री");
        h.setOrderType("स्थगित");
        h.setBench("1");
        h.setSerialNo("क");
        return h;
    }

    @Test
    @DisplayName("Writes a BOM-prefixed, quoted CSV for the Mon–Sun window with matched flags")
    void exportsWindow() throws IOException {
        LocalDate monday = LocalDate.of(2026, 8, 10);
        LocalDate sunday = LocalDate.of(2026, 8, 16);

        DailyHearing inWindowMatched = daily(39, LocalDate.of(2026, 8, 12), "39-081-39885",
                "लेनदेन, चेक अनादर");
        DailyHearing inWindowUnmatched = daily(63, LocalDate.of(2026, 8, 13), "63-082-00057",
                "अंशचलन");
        WeeklyHearing weekly = new WeeklyHearing();
        weekly.setCourtId(39);
        weekly.setHearingDateBs("2083-05-02");
        weekly.setHearingDateAd(LocalDate.of(2026, 8, 14));
        weekly.setCaseNoInternal("39-082-06709");
        weekly.setSubject("जालसाजी");

        HearingMatch matched = new HearingMatch();
        matched.setCourtId(39);
        matched.setCaseNoInternal("39-081-39885");
        matched.setHearingDateBs("2083-05-01");

        // The between-window filter lives in the repository query — the mock returns only in-window rows.
        when(dailyRepository.findByHearingDateAdBetweenOrderByCourtIdAscHearingDateAdAsc(monday, sunday))
                .thenReturn(List.of(inWindowMatched, inWindowUnmatched));
        when(weeklyRepository.findByHearingDateAdBetweenOrderByCourtIdAscHearingDateAdAsc(monday, sunday))
                .thenReturn(List.of(weekly));
        when(matchRepository.findByHearingDateAdBetween(monday, sunday)).thenReturn(List.of(matched));

        Path file = service.export(monday, sunday);

        assertEquals("week-2026-08-10_2026-08-16.csv", file.getFileName().toString());
        String content = Files.readString(file, StandardCharsets.UTF_8);

        assertTrue(content.startsWith("\uFEFF"), "must start with UTF-8 BOM for Excel");
        String[] lines = content.split("\n");
        // header + 2 daily rows + 1 weekly row
        assertEquals(4, lines.length);
        assertEquals("courtId,hearingDateBs,hearingDateAd,caseNoBs,caseNoInternal,"
                + "bench,serialNo,judgeName,subject,plaintiff,defendant,orderType,source,matched",
                lines[0].replace("\uFEFF", ""));

        String matchedLine = java.util.Arrays.stream(lines)
                .filter(l -> l.contains("39-081-39885")).findFirst().orElseThrow();
        String unmatchedLine = java.util.Arrays.stream(lines)
                .filter(l -> l.contains("63-082-00057")).findFirst().orElseThrow();
        String weeklyLine = java.util.Arrays.stream(lines)
                .filter(l -> l.contains("39-082-06709")).findFirst().orElseThrow();

        assertTrue(matchedLine.startsWith("\"39\",\"2083-05-01\",\"2026-08-12\",\"081-C1-7530\","),
                "row must be quoted: " + matchedLine);
        assertTrue(matchedLine.contains(",\"1\",\"क\",\"माननीय"), "bench and serial must be exported: " + matchedLine);
        assertTrue(matchedLine.contains("\"लेनदेन, चेक अनादर\""), "commas inside fields must be quoted");
        assertTrue(matchedLine.endsWith(",\"DAILY\",\"Y\""), "matched row flagged Y");
        assertTrue(unmatchedLine.endsWith(",\"DAILY\",\"N\""), "unmatched row flagged N");
        assertTrue(weeklyLine.endsWith(",\"WEEKLY\",\"N\""));
    }

    @Test
    @DisplayName("exportLastWeek computes the previous completed Mon–Sun week")
    void lastWeekWindow() throws IOException {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (monday.equals(today)) monday = monday.minusWeeks(1);

        when(dailyRepository.findByHearingDateAdBetweenOrderByCourtIdAscHearingDateAdAsc(any(), any()))
                .thenReturn(List.of());
        when(weeklyRepository.findByHearingDateAdBetweenOrderByCourtIdAscHearingDateAdAsc(any(), any()))
                .thenReturn(List.of());
        when(matchRepository.findByHearingDateAdBetween(any(), any())).thenReturn(List.of());

        Path file = service.exportLastWeek();

        assertTrue(file.getFileName().toString().startsWith(
                "week-" + monday + "_" + monday.plusDays(6)),
                "unexpected file name: " + file.getFileName());
        // Re-running overwrites the same file (idempotent), no .tmp left behind.
        assertTrue(Files.exists(file));
        try (var tmp = Files.list(tempDir)) {
            assertTrue(tmp.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    @DisplayName("Empty week still produces a header-only file")
    void emptyWeek() throws IOException {
        when(dailyRepository.findByHearingDateAdBetweenOrderByCourtIdAscHearingDateAdAsc(any(), any()))
                .thenReturn(List.of());
        when(weeklyRepository.findByHearingDateAdBetweenOrderByCourtIdAscHearingDateAdAsc(any(), any()))
                .thenReturn(List.of());
        when(matchRepository.findByHearingDateAdBetween(any(), any())).thenReturn(List.of());

        Path file = service.export(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 16));

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals(1, content.split("\n").length, "header only");
    }
}
