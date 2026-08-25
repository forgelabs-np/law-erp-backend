package com.lawfirm.erp.modules.scraper.controller;

import com.lawfirm.erp.common.constant.ScraperConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.scraper.dto.CaseDetailResponse;
import com.lawfirm.erp.modules.scraper.dto.HearingStatusResponse;
import com.lawfirm.erp.modules.scraper.service.ScraperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
@Tag(name = "Hearing Status", description = "Read-only hearing lookups against our own ingested data")
public class HearingStatusController {

    private final ScraperService scraperService;
    private final ResponseHandler responseHandler;

    @GetMapping("/{caseNo}/hearing-status")
    @Operation(summary = ScraperConstants.HEARING_STATUS_SUMMARY, description = ScraperConstants.HEARING_STATUS_DESCRIPTION)
    public ResponseEntity<ApiResponse<HearingStatusResponse>> hearingStatus(
            @PathVariable String caseNo,
            @RequestParam(required = false) String date) {
        return responseHandler.ok(scraperService.getHearingStatus(caseNo, date),
                "Hearing status fetched successfully");
    }

    @GetMapping("/live-detail")
    @Operation(summary = ScraperConstants.LIVE_DETAIL_SUMMARY, description = ScraperConstants.LIVE_DETAIL_DESCRIPTION)
    public ResponseEntity<ApiResponse<CaseDetailResponse>> liveDetail(
            @RequestParam Integer courtId,
            @RequestParam String caseNo) {
        return responseHandler.ok(scraperService.getCaseDetailLive(courtId, caseNo),
                "Live case detail fetched");
    }
}
