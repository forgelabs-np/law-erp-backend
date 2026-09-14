package com.lawfirm.erp.modules.scraper.service;

import com.lawfirm.erp.modules.scraper.dto.CaseDetailResponse;
import com.lawfirm.erp.modules.scraper.dto.HearingStatusResponse;
import com.lawfirm.erp.modules.scraper.dto.ScrapeRunResult;
import com.lawfirm.erp.modules.scraper.entity.ClientCase;
import com.lawfirm.erp.modules.scraper.entity.Court;

import java.util.List;
import java.util.Optional;

public interface ScraperService {

    List<Court> getAllCourts();
    List<Court> getCourtsByType(String courtType);
    List<Integer> getActiveCourts();

    List<ScrapeRunResult> runDailyScrape(String dateBs);

    List<ScrapeRunResult> runWeeklyScrape();

    ScrapeRunResult runDailyScrapeForCourt(Integer courtId, String dateBs);

    ScrapeRunResult runWeeklyScrapeForCourt(Integer courtId);

    CaseDetailResponse getCaseDetailLive(Integer courtId, String caseNoBs);

    HearingStatusResponse getHearingStatus(String caseNoInternal);

    HearingStatusResponse getHearingStatus(String caseNoInternal, String dateBs);

    Optional<ClientCase> findClientCaseByCaseNo(String caseNoInternal);
}
