package com.lawfirm.erp.modules.scraper.service;

import com.lawfirm.erp.modules.scraper.client.CourtSiteClient;
import com.lawfirm.erp.modules.scraper.config.ScraperProperties;
import com.lawfirm.erp.modules.scraper.converter.NepaliDateUtil;
import com.lawfirm.erp.modules.scraper.dto.CaseDetailResponse;
import com.lawfirm.erp.modules.scraper.dto.HearingRecord;
import com.lawfirm.erp.modules.scraper.dto.HearingStatusResponse;
import com.lawfirm.erp.modules.scraper.dto.ScrapeRunResult;
import com.lawfirm.erp.modules.scraper.entity.Court;
import com.lawfirm.erp.modules.scraper.entity.DailyHearing;
import com.lawfirm.erp.modules.scraper.entity.WeeklyHearing;
import com.lawfirm.erp.modules.scraper.enums.HearingSource;
import com.lawfirm.erp.modules.scraper.parser.CaseDetailParser;
import com.lawfirm.erp.modules.scraper.parser.DailyTableParser;
import com.lawfirm.erp.modules.scraper.parser.WeeklyTableParser;
import com.lawfirm.erp.modules.scraper.repository.ClientCaseRepository;
import com.lawfirm.erp.modules.scraper.repository.CourtRepository;
import com.lawfirm.erp.modules.scraper.repository.DailyHearingRepository;
import com.lawfirm.erp.modules.scraper.repository.WeeklyHearingRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// Orchestrates a scrape run: derive active courts from client cases, fan out one scrape per
// court on a bounded executor, upsert, then match + notify. Failures and 0-row results are
// logged per court and never stop the rest of the run.
@Slf4j
@Service
@RequiredArgsConstructor
public class ScraperService {

    private final CourtSiteClient courtSiteClient;
    private final DailyTableParser dailyParser;
    private final WeeklyTableParser weeklyParser;
    private final CaseDetailParser caseDetailParser;
    private final HearingIngestionService ingestionService;
    private final HearingMatchingService matchingService;
    private final ClientCaseRepository clientCaseRepository;
    private final CourtRepository courtRepository;
    private final DailyHearingRepository dailyHearingRepository;
    private final WeeklyHearingRepository weeklyHearingRepository;
    private final ScraperProperties properties;

    private ExecutorService executor;

    @PostConstruct
    void init() {
        executor = Executors.newFixedThreadPool(properties.getMaxConcurrentCourts());
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /** Test hook: replace the executor (e.g. a direct executor for deterministic tests). */
    void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    public List<Integer> getActiveCourts() {
        return clientCaseRepository.findDistinctActiveCourtIds();
    }

    public List<ScrapeRunResult> runDailyScrape(String dateBs) {
        if (!properties.isEnabled()) {
            log.info("Scraper disabled — skipping daily run");
            return List.of();
        }
        LocalDate dateAd = NepaliDateUtil.bsToAd(dateBs);
        List<Integer> courts = getActiveCourts();
        log.info("Daily scrape: {} active court(s) for {}", courts.size(), dateBs);

        List<Future<ScrapeRunResult>> futures = new ArrayList<>();
        for (Integer courtId : courts) {
            futures.add(executor.submit(() -> scrapeCourtDaily(courtId, dateBs, dateAd)));
        }
        List<ScrapeRunResult> results = collect(futures);

        matchingService.matchAndNotify();
        return results;
    }

    public List<ScrapeRunResult> runWeeklyScrape() {
        if (!properties.isEnabled()) {
            log.info("Scraper disabled — skipping weekly run");
            return List.of();
        }
        List<Integer> courts = getActiveCourts();
        log.info("Weekly scrape: {} active court(s)", courts.size());

        List<Future<ScrapeRunResult>> futures = new ArrayList<>();
        for (Integer courtId : courts) {
            futures.add(executor.submit(() -> scrapeCourtWeekly(courtId)));
        }
        List<ScrapeRunResult> results = collect(futures);

        matchingService.matchAndNotify();
        return results;
    }

    public ScrapeRunResult runDailyScrapeForCourt(Integer courtId, String dateBs) {
        LocalDate dateAd = NepaliDateUtil.bsToAd(dateBs);
        return scrapeCourtDaily(courtId, dateBs, dateAd);
    }

    public ScrapeRunResult runWeeklyScrapeForCourt(Integer courtId) {
        return scrapeCourtWeekly(courtId);
    }

    // Live lookup against the court site — the case_process_detail feed holds the case's full
    // history regardless of what we've scraped. Takes the display form (081-C1-7530).
    public CaseDetailResponse getCaseDetailLive(Integer courtId, String caseNoBs) {
        String html = courtSiteClient.scrapeCaseDetail(courtId, caseNoBs);
        return caseDetailParser.parse(html, courtId);
    }

    private ScrapeRunResult scrapeCourtDaily(Integer courtId, String dateBs, LocalDate dateAd) {
        try {
            String html = courtSiteClient.scrapeDaily(courtId, dateBs);
            List<HearingRecord> records = dailyParser.parse(html, courtId);
            // The daily page is the requested date's list — stamp it on every row.
            for (HearingRecord r : records) {
                r.setHearingDateBs(dateBs);
                r.setHearingDateAd(dateAd);
            }
            int rows = ingestionService.upsertDaily(records);
            if (records.isEmpty()) {
                alert("court " + courtId + " daily " + dateBs + " returned 0 rows (list not published?)");
            }
            return ScrapeRunResult.builder()
                    .courtId(courtId).success(true).rows(rows).build();
        } catch (Exception e) {
            log.error("Daily scrape failed for court {} ({}): {}", courtId, dateBs, e.getMessage(), e);
            alert("court " + courtId + " daily scrape failed: " + e.getMessage());
            return ScrapeRunResult.builder()
                    .courtId(courtId).success(false).error(e.getMessage()).build();
        }
    }

    private ScrapeRunResult scrapeCourtWeekly(Integer courtId) {
        try {
            String html = courtSiteClient.scrapeWeekly(courtId);
            List<HearingRecord> records = weeklyParser.parse(html, courtId);
            int rows = ingestionService.upsertWeekly(records);
            if (records.isEmpty()) {
                alert("court " + courtId + " weekly returned 0 rows");
            }
            return ScrapeRunResult.builder()
                    .courtId(courtId).success(true).rows(rows).build();
        } catch (Exception e) {
            log.error("Weekly scrape failed for court {}: {}", courtId, e.getMessage(), e);
            alert("court " + courtId + " weekly scrape failed: " + e.getMessage());
            return ScrapeRunResult.builder()
                    .courtId(courtId).success(false).error(e.getMessage()).build();
        }
    }

    public HearingStatusResponse getHearingStatus(String caseNoInternal) {
        return getHearingStatus(caseNoInternal, null);
    }

    // DB only, no live scrape; optional BS date filters to one day's list.
    public HearingStatusResponse getHearingStatus(String caseNoInternal, String dateBs) {
        LocalDate today = LocalDate.now();
        List<HearingStatusResponse.Hearing> upcoming = new ArrayList<>();
        List<HearingStatusResponse.Hearing> history = new ArrayList<>();

        if (dateBs != null && !dateBs.isBlank()) {
            for (DailyHearing h : dailyHearingRepository.findByCaseNoInternalAndHearingDateBs(caseNoInternal, dateBs)) {
                addHearing(upcoming, history, today, h.getHearingDateAd(), h.getHearingDateBs(),
                        h.getJudgeName(), h.getSubject(), h.getOrderType(), HearingSource.DAILY);
            }
            for (WeeklyHearing h : weeklyHearingRepository.findByCaseNoInternalAndHearingDateBs(caseNoInternal, dateBs)) {
                addHearing(upcoming, history, today, h.getHearingDateAd(), h.getHearingDateBs(),
                        h.getJudgeName(), h.getSubject(), h.getOrderType(), HearingSource.WEEKLY);
            }
        } else {
            for (DailyHearing h : dailyHearingRepository.findByCaseNoInternalOrderByHearingDateAdDesc(caseNoInternal)) {
                addHearing(upcoming, history, today, h.getHearingDateAd(), h.getHearingDateBs(),
                        h.getJudgeName(), h.getSubject(), h.getOrderType(), HearingSource.DAILY);
            }
            for (WeeklyHearing h : weeklyHearingRepository.findByCaseNoInternalOrderByHearingDateAdDesc(caseNoInternal)) {
                addHearing(upcoming, history, today, h.getHearingDateAd(), h.getHearingDateBs(),
                        h.getJudgeName(), h.getSubject(), h.getOrderType(), HearingSource.WEEKLY);
            }
        }
        upcoming.sort(Comparator.comparing(HearingStatusResponse.Hearing::getHearingDateAd,
                Comparator.nullsLast(Comparator.naturalOrder())));
        history.sort(Comparator.comparing(HearingStatusResponse.Hearing::getHearingDateAd,
                Comparator.nullsLast(Comparator.reverseOrder())));

        String caseNoBs = null;
        Integer courtId = null;
        String courtName = null;
        var cc = clientCaseRepository.findByCaseNoInternal(caseNoInternal);
        if (cc.isPresent()) {
            caseNoBs = cc.get().getCaseNoBs();
            courtId = cc.get().getCourtId();
            courtName = courtRepository.findByCourtId(courtId)
                    .map(Court::getCourtName).orElse(null);
        }

        return HearingStatusResponse.builder()
                .caseNoInternal(caseNoInternal)
                .caseNoBs(caseNoBs)
                .courtId(courtId)
                .courtName(courtName)
                .upcoming(upcoming)
                .history(history)
                .build();
    }

    private void addHearing(List<HearingStatusResponse.Hearing> upcoming,
                            List<HearingStatusResponse.Hearing> history,
                            LocalDate today, LocalDate ad, String bs, String judge,
                            String subject, String orderType, HearingSource source) {
        HearingStatusResponse.Hearing h = HearingStatusResponse.Hearing.builder()
                .hearingDateAd(ad).hearingDateBs(bs).judgeName(judge)
                .subject(subject).orderType(orderType).source(source)
                .build();
        if (ad != null && !ad.isBefore(today)) {
            upcoming.add(h);
        } else {
            history.add(h);
        }
    }

    private List<ScrapeRunResult> collect(List<Future<ScrapeRunResult>> futures) {
        List<ScrapeRunResult> results = new ArrayList<>();
        for (Future<ScrapeRunResult> f : futures) {
            try {
                results.add(f.get());
            } catch (Exception e) {
                log.error("Scrape task failed unexpectedly: {}", e.getMessage(), e);
            }
        }
        return results;
    }

    private void alert(String message) {
        log.error("[scraper-alert] {}", message);
    }
}
