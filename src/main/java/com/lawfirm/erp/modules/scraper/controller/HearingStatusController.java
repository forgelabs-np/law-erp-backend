package com.lawfirm.erp.modules.scraper.controller;

import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.scraper.dto.CaseDetailResponse;
import com.lawfirm.erp.modules.scraper.dto.HearingStatusResponse;
import com.lawfirm.erp.modules.scraper.service.ScraperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
@Tag(name = "Hearing Status", description = "Read-only hearing lookups against our own ingested data")
@PreAuthorize("hasAnyRole('FIRM_ADMIN', 'ADVOCATE', 'PARALEGAL')")
public class HearingStatusController {

    private final ScraperService scraperService;
    private final ResponseHandler responseHandler;

    @GetMapping("/{caseNo}/hearing-status")
    @Operation(summary = "Hearing status for a case",
            description = "Upcoming + past hearings for a court-scoped internal case number (e.g. 39-081-32030). Optional date filter (BS yyyy-mm-dd) returns only that day's list entries. DB only — no live scrape on this path.")
    public ResponseEntity<ApiResponse<HearingStatusResponse>> hearingStatus(
            @PathVariable String caseNo,
            @RequestParam(required = false) String date) {
        return responseHandler.ok(scraperService.getHearingStatus(caseNo, date),
                "Hearing status fetched successfully");
    }

    @GetMapping("/live-detail")
    @Operation(summary = "Live case detail from the court site",
            description = "Hits the site's case_process_detail feed for the case's full history — no prior scrape needed. "
                    + "caseNo is the display form (e.g. 081-C1-7530), courtId matches the site path segment (39 = Kathmandu). "
                    + "found=false when the case number doesn't exist on the site.")
    public ResponseEntity<ApiResponse<CaseDetailResponse>> liveDetail(
            @RequestParam Integer courtId,
            @RequestParam String caseNo) {
        return responseHandler.ok(scraperService.getCaseDetailLive(courtId, caseNo),
                "Live case detail fetched");
    }
}
