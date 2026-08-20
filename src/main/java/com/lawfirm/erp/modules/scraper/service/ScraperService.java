package com.lawfirm.erp.modules.scraper.service;

import com.lawfirm.erp.modules.scraper.dto.CaseDetailResponse;
import com.lawfirm.erp.modules.scraper.dto.HearingStatusResponse;
import com.lawfirm.erp.modules.scraper.dto.ScrapeRunResult;

import java.util.List;

public interface ScraperService {

    List<Integer> getActiveCourts();

    List<ScrapeRunResult> runDailyScrape(String dateBs);

    List<ScrapeRunResult> runWeeklyScrape();

    ScrapeRunResult runDailyScrapeForCourt(Integer courtId, String dateBs);

    ScrapeRunResult runWeeklyScrapeForCourt(Integer courtId);

    CaseDetailResponse getCaseDetailLive(Integer courtId, String caseNoBs);

    HearingStatusResponse getHearingStatus(String caseNoInternal);

    HearingStatusResponse getHearingStatus(String caseNoInternal, String dateBs);
}
