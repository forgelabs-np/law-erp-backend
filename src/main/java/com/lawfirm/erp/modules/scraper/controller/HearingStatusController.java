package com.lawfirm.erp.modules.scraper.controller;

import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
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
            description = "Upcoming + past hearings for a court-scoped internal case number (e.g. 39-081-32030). DB only — no live scrape on this path.")
    public ResponseEntity<ApiResponse<HearingStatusResponse>> hearingStatus(@PathVariable String caseNo) {
        return responseHandler.ok(scraperService.getHearingStatus(caseNo),
                "Hearing status fetched successfully");
    }
}
