package com.lawfirm.erp.modules.scraper.client;

/**
 * The court site's scrape surface. Implementations must establish a session (GET for
 * cookies) before POSTing the form. Kept behind an interface so the HTTP-backed default
 * can be swapped for a browser-backed (Selenium/Playwright) impl if the site ever gates
 * content on client-side JS.
 */
public interface CourtSiteClient {

    /**
     * Fetch the daily cause list for a court on a BS date.
     *
     * @param courtId site court id (39 = Kathmandu, 63 = Gulmi)
     * @param dateBs  hearing date in BS, Arabic digits, yyyy-mm-dd
     * @return the response HTML (form page only when the list is not published)
     */
    String scrapeDaily(int courtId, String dateBs);

    /** Fetch the weekly cause list (Sun–Fri window) for a court. */
    String scrapeWeekly(int courtId);
}
