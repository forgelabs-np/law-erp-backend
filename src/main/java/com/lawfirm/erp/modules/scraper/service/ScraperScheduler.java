package com.lawfirm.erp.modules.scraper.service;

import com.lawfirm.erp.modules.scraper.config.ScraperProperties;
import com.lawfirm.erp.modules.scraper.converter.NepaliDateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// Scheduled entry points; crons overridable via scraper.*-cron. All no-op when scraper.enabled=false.
@Slf4j
@Component
@RequiredArgsConstructor
public class ScraperScheduler {

    private final ScraperService scraperService;
    private final HearingExportService exportService;
    private final ScraperProperties properties;

    @Scheduled(cron = "${scraper.daily-cron:0 5 10 * * MON-FRI}")  // 10:05 AM Nepal time, Mon-Fri
    public void runDaily() {
        if (!properties.isEnabled()) return;
        String todayBs = NepaliDateUtil.adToBs(LocalDate.now());
        if (todayBs == null) {
            log.warn("Could not compute today's BS date — skipping daily scrape");
            return;
        }
        scraperService.runDailyScrape(todayBs);
    }

    @Scheduled(cron = "${scraper.weekly-cron:0 15 10 * * MON}")
    public void runWeekly() {
        if (!properties.isEnabled()) return;
        scraperService.runWeeklyScrape();
    }

    @Scheduled(cron = "${scraper.export-cron:0 30 10 * * MON}")
    public void exportWeeklyCsv() {
        if (!properties.isEnabled()) return;
        try {
            exportService.exportLastWeek();
        } catch (Exception e) {
            log.error("[scraper-alert] weekly CSV export failed: {}", e.getMessage(), e);
        }
    }
}
