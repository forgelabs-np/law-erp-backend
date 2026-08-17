package com.lawfirm.erp.modules.scraper.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// Scraper knobs, overridable in application.yml under `scraper:`.
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "scraper")
public class ScraperProperties {

    private String baseUrl = "https://supremecourt.gov.np";
    private boolean enabled = true;
    private long requestDelayMs = 1500;
    private int connectTimeoutMs = 15000;
    private int readTimeoutMs = 60000;
    private int maxConcurrentCourts = 5;
    private String exportDir = "./exports";
}
