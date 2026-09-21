package com.lawfirm.erp.modules.scraper.service;

import com.lawfirm.erp.modules.scraper.dto.CaseDetailResponse;
import com.lawfirm.erp.modules.scraper.dto.HearingStatusResponse;
import com.lawfirm.erp.modules.scraper.dto.ScrapeRunResult;
import com.lawfirm.erp.modules.scraper.entity.ClientCase;
import com.lawfirm.erp.modules.scraper.entity.Court;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    /**
     * Link a case management court case to a scraper client case.
     * Creates or updates the client case in the scraper with court's official number.
     */
    ClientCase linkCourtCaseToScraper(Integer courtId, String courtCaseNumber, 
                                       String caseNoInternal, UUID clientId);

    /**
     * Find client case by court's official number (Nepali BS format).
     */
    Optional<ClientCase> findByCourtIdAndCaseNoBs(Integer courtId, String caseNoBs);

    /**
     * Get all tracked cases for a specific client.
     */
    List<ClientCase> getClientCases(UUID clientId);
}
