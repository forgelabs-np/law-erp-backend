package com.lawfirm.erp.common.constant;

public final class ScraperConstants {

    private ScraperConstants() {}

    // HearingStatusController
    public static final String HEARING_STATUS_SUMMARY = "Hearing status for a case";
    public static final String HEARING_STATUS_DESCRIPTION = "Upcoming + past hearings for a court-scoped internal case number (e.g. 39-081-32030). Optional date filter (BS yyyy-mm-dd) returns only that day's list entries. DB only — no live scrape on this path.";

    public static final String LIVE_DETAIL_SUMMARY = "Live case detail from the court site";
    public static final String LIVE_DETAIL_DESCRIPTION = "Hits the site's case_process_detail feed for the case's full history — no prior scrape needed. caseNo is the display form (e.g. 081-C1-7530), courtId matches the site path segment (39 = Kathmandu). found=false when the case number doesn't exist on the site.";

    // ScraperAdminController
    public static final String SCRAPE_SUMMARY = "Trigger a scrape for one court";
    public static final String SCRAPE_DESCRIPTION = "Hits the court site live for a single court. mode=daily (default) or weekly; date is BS yyyy-mm-dd and only applies to mode=daily (defaults to today).";

    public static final String EXPORT_SUMMARY = "Regenerate the weekly CSV export";
    public static final String EXPORT_DESCRIPTION = "Re-exports the previous completed Monday–Sunday window to <export-dir>/week-<monday>_<sunday>.csv (overwrites).";
}
