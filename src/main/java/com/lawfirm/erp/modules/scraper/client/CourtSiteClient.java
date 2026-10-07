package com.lawfirm.erp.modules.scraper.client;

public interface CourtSiteClient {

    String scrapeDaily(int courtId, String dateBs);

    String scrapeWeekly(int courtId);

    String scrapeCaseDetail(int courtId, String caseNoBs);
}
