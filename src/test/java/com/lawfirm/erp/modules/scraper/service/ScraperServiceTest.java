package com.lawfirm.erp.modules.scraper.service;

import com.lawfirm.erp.modules.scraper.client.CourtSiteClient;
import com.lawfirm.erp.modules.scraper.config.ScraperProperties;
import com.lawfirm.erp.modules.scraper.dto.HearingRecord;
import com.lawfirm.erp.modules.scraper.dto.HearingStatusResponse;
import com.lawfirm.erp.modules.scraper.dto.ScrapeRunResult;
import com.lawfirm.erp.modules.scraper.entity.ClientCase;
import com.lawfirm.erp.modules.scraper.entity.Court;
import com.lawfirm.erp.modules.scraper.entity.DailyHearing;
import com.lawfirm.erp.modules.scraper.enums.HearingSource;
import com.lawfirm.erp.modules.scraper.parser.CaseDetailParser;
import com.lawfirm.erp.modules.scraper.parser.DailyTableParser;
import com.lawfirm.erp.modules.scraper.parser.WeeklyTableParser;
import com.lawfirm.erp.modules.scraper.repository.ClientCaseRepository;
import com.lawfirm.erp.modules.scraper.repository.CourtRepository;
import com.lawfirm.erp.modules.scraper.repository.DailyHearingRepository;
import com.lawfirm.erp.modules.scraper.repository.WeeklyHearingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScraperServiceTest {

    @Mock private CourtSiteClient courtSiteClient;
    @Mock private DailyTableParser dailyParser;
    @Mock private WeeklyTableParser weeklyParser;
    @Mock private CaseDetailParser caseDetailParser;
    @Mock private HearingIngestionService ingestionService;
    @Mock private HearingMatchingService matchingService;
    @Mock private ClientCaseRepository clientCaseRepository;
    @Mock private CourtRepository courtRepository;
    @Mock private DailyHearingRepository dailyHearingRepository;
    @Mock private WeeklyHearingRepository weeklyHearingRepository;

    private ScraperProperties properties;
    private ScraperService service;

    @BeforeEach
    void setUp() {
        properties = new ScraperProperties();
        properties.setEnabled(true);
        properties.setMaxConcurrentCourts(4);
        properties.setRequestDelayMs(0);
        service = new ScraperService(courtSiteClient, dailyParser, weeklyParser, caseDetailParser,
                ingestionService, matchingService, clientCaseRepository, courtRepository,
                dailyHearingRepository, weeklyHearingRepository, properties);
        service.init();
    }

    private HearingRecord record(int courtId) {
        return HearingRecord.builder()
                .courtId(courtId)
                .hearingDateBs("2083-05-01")
                .hearingDateAd(LocalDate.of(2026, 8, 17))
                .caseNoInternal("39-081-32030")
                .source(HearingSource.DAILY)
                .build();
    }

    @Test
    @DisplayName("Derives courts from client cases and runs all of them, then matches")
    void fullDailyRun() {
        when(clientCaseRepository.findDistinctActiveCourtIds()).thenReturn(List.of(39, 63));
        when(courtSiteClient.scrapeDaily(anyInt(), any())).thenReturn("<html>...</html>");
        when(dailyParser.parse(any(), any())).thenReturn(List.of(record(39)));
        when(ingestionService.upsertDaily(any())).thenReturn(1);

        List<ScrapeRunResult> results = service.runDailyScrape("2083-05-01");

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(ScrapeRunResult::isSuccess));
        verify(courtSiteClient, times(2)).scrapeDaily(anyInt(), any());
        verify(matchingService, times(1)).matchAndNotify();
    }

    @Test
    @DisplayName("0-row result is reported as success with zero rows (list not published)")
    void zeroRows() {
        when(clientCaseRepository.findDistinctActiveCourtIds()).thenReturn(List.of(39));
        when(courtSiteClient.scrapeDaily(anyInt(), any())).thenReturn("<html>no rows</html>");
        when(dailyParser.parse(any(), any())).thenReturn(List.of());

        List<ScrapeRunResult> results = service.runDailyScrape("2083-05-01");

        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        assertEquals(0, results.get(0).getRows());
        verify(matchingService, times(1)).matchAndNotify();
    }

    @Test
    @DisplayName("A failing court scrape is reported as failure and does not stop matching")
    void failureDoesNotStopRun() {
        when(clientCaseRepository.findDistinctActiveCourtIds()).thenReturn(List.of(39, 63));
        when(courtSiteClient.scrapeDaily(eq(39), any())).thenThrow(new RuntimeException("timeout"));
        when(courtSiteClient.scrapeDaily(eq(63), any())).thenReturn("<html></html>");
        when(dailyParser.parse(any(), any())).thenReturn(List.of(record(63)));

        List<ScrapeRunResult> results = service.runDailyScrape("2083-05-01");

        assertEquals(2, results.size());
        ScrapeRunResult failed = results.stream().filter(r -> r.getCourtId() == 39).findFirst().orElseThrow();
        assertFalse(failed.isSuccess());
        assertNotNull(failed.getError());
        ScrapeRunResult ok = results.stream().filter(r -> r.getCourtId() == 63).findFirst().orElseThrow();
        assertTrue(ok.isSuccess());
        verify(matchingService, times(1)).matchAndNotify();
    }

    @Test
    @DisplayName("Disabled scraper no-ops without touching the site")
    void disabled() {
        properties.setEnabled(false);
        List<ScrapeRunResult> results = service.runDailyScrape("2083-05-01");
        assertTrue(results.isEmpty());
        verifyNoInteractions(courtSiteClient);
    }

    @Test
    @DisplayName("Hearing-status read path splits upcoming vs history using only our DB")
    void hearingStatus() {
        DailyHearing upcoming = new DailyHearing();
        upcoming.setCaseNoInternal("39-081-32030");
        upcoming.setHearingDateBs("2083-05-02");
        upcoming.setHearingDateAd(LocalDate.now().plusDays(2));
        upcoming.setJudgeName("इजलाश 1");
        DailyHearing past = new DailyHearing();
        past.setCaseNoInternal("39-081-32030");
        past.setHearingDateBs("2083-04-25");
        past.setHearingDateAd(LocalDate.now().minusDays(3));

        when(dailyHearingRepository.findByCaseNoInternalOrderByHearingDateAdDesc("39-081-32030"))
                .thenReturn(List.of(upcoming, past));
        when(weeklyHearingRepository.findByCaseNoInternalOrderByHearingDateAdDesc(any()))
                .thenReturn(List.of());
        ClientCase cc = new ClientCase();
        cc.setCourtId(39);
        cc.setCaseNoBs("081-C4-3827");
        when(clientCaseRepository.findByCaseNoInternal("39-081-32030")).thenReturn(Optional.of(cc));
        Court court = new Court();
        court.setCourtId(39);
        court.setCourtName("काठमाडौं जिल्ला अदालत");
        when(courtRepository.findByCourtId(39)).thenReturn(Optional.of(court));

        HearingStatusResponse status = service.getHearingStatus("39-081-32030");

        assertEquals(1, status.getUpcoming().size());
        assertEquals(1, status.getHistory().size());
        assertEquals("काठमाडौं जिल्ला अदालत", status.getCourtName());
        assertEquals("081-C4-3827", status.getCaseNoBs());
        verifyNoInteractions(courtSiteClient);
    }

    @Test
    @DisplayName("Hearing-status with a date filter returns only that BS date's rows")
    void hearingStatusFilteredByDate() {
        DailyHearing onDate = new DailyHearing();
        onDate.setCaseNoInternal("39-081-32030");
        onDate.setHearingDateBs("2083-04-29");
        onDate.setHearingDateAd(LocalDate.now().minusDays(1));
        onDate.setJudgeName("इजलाश 1");
        onDate.setSubject("लेनदेन");

        when(dailyHearingRepository.findByCaseNoInternalAndHearingDateBs("39-081-32030", "2083-04-29"))
                .thenReturn(List.of(onDate));
        when(weeklyHearingRepository.findByCaseNoInternalAndHearingDateBs(any(), any()))
                .thenReturn(List.of());
        when(clientCaseRepository.findByCaseNoInternal("39-081-32030")).thenReturn(Optional.empty());

        HearingStatusResponse status = service.getHearingStatus("39-081-32030", "2083-04-29");

        assertEquals(1, status.getHistory().size());
        assertEquals("2083-04-29", status.getHistory().get(0).getHearingDateBs());
        assertEquals("लेनदेन", status.getHistory().get(0).getSubject());
        verify(dailyHearingRepository, never()).findByCaseNoInternalOrderByHearingDateAdDesc(any());
        verifyNoInteractions(courtSiteClient);
    }

    @Test
    @DisplayName("Live case detail hits the site with the display-form number and returns parsed result")
    void liveDetail() {
        var detail = com.lawfirm.erp.modules.scraper.dto.CaseDetailResponse.builder()
                .found(true).courtId(39).caseNoBs("081-C1-7530")
                .caseNoInternal("39-081-39885").status("चालु").build();
        when(courtSiteClient.scrapeCaseDetail(39, "081-C1-7530")).thenReturn("<html>detail</html>");
        when(caseDetailParser.parse("<html>detail</html>", 39)).thenReturn(detail);

        var result = service.getCaseDetailLive(39, "081-C1-7530");

        assertTrue(result.isFound());
        assertEquals("39-081-39885", result.getCaseNoInternal());
        verify(courtSiteClient, times(1)).scrapeCaseDetail(39, "081-C1-7530");
    }
}
