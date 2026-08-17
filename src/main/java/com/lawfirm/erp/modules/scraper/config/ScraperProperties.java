package com.lawfirm.erp.modules.scraper.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Scraper tuning knobs. All overridable via application.yml under `scraper:`.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "scraper")
public class ScraperProperties {

    /** Base URL of the Supreme Court cause-list site. */
    private String baseUrl = "https://supremecourt.gov.np";

    /** Master switch — when false, the scheduled jobs no-op (useful before go-live). */
    private boolean enabled = true;

    /** Politeness delay between HTTP requests to the court site, in milliseconds. */
    private long requestDelayMs = 1500;

    /** Connection / read timeouts for court-site requests, in milliseconds. */
    private int connectTimeoutMs = 15000;
    private int readTimeoutMs = 60000;

    /** Max courts scraped concurrently per run (asyncio.gather fan-out here = a bounded executor). */
    private int maxConcurrentCourts = 5;

    /** Directory for the weekly CSV export (created if missing). */
    private String exportDir = "./exports";
}
