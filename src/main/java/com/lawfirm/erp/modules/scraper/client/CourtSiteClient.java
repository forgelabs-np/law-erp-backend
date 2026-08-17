package com.lawfirm.erp.modules.scraper.client;

// Scrape surface for the court site. Session (GET for cookies) must be established before
// POSTing the form. Interface so a browser-backed impl can replace the HTTP one if needed.
public interface CourtSiteClient {

    String scrapeDaily(int courtId, String dateBs);

    String scrapeWeekly(int courtId);

    String scrapeCaseDetail(int courtId, String caseNoBs);
}
