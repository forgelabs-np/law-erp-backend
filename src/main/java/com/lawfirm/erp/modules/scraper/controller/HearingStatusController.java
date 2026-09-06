package com.lawfirm.erp.modules.scraper.controller;

import com.lawfirm.erp.common.constant.ScraperConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.scraper.dto.CaseDetailResponse;
import com.lawfirm.erp.modules.scraper.dto.HearingStatusResponse;
import com.lawfirm.erp.modules.scraper.entity.HearingMatch;
import com.lawfirm.erp.modules.scraper.service.ScraperService;
import com.lawfirm.erp.modules.scraper.service.HearingMatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
@Tag(name = "Hearing Status", description = "Read-only hearing lookups against our own ingested data")
public class HearingStatusController {

    private final ScraperService scraperService;
    private final HearingMatchingService matchingService;
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

    /**
     * All court matches for a case, newest first.
     */
    @GetMapping("/{caseNo}/matches")
    @Operation(summary = "All hearing matches for a case",
            description = "Returns every matched hearing (scraped row joined to this case) ordered by date.")
    public ResponseEntity<ApiResponse<List<HearingMatchDto>>> matches(
            @PathVariable String caseNo) {
        var cc = scraperService.findClientCaseByCaseNo(caseNo)
                .orElseThrow(() -> new ResourceNotFoundException("No client case found for caseNo: " + caseNo));
        List<HearingMatch> matches = matchingService.findByClientCaseId(cc.getId());
        List<HearingMatchDto> dtos = matches.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return responseHandler.ok(dtos, "Matches fetched successfully");
    }

    /**
     * Un-notified matches across all cases — the dispatch pending queue.
     */
    @GetMapping("/matches/unnotified")
    // Admin/ops visibility into the dispatch queue; caller must hold the scraper permission.
    @Operation(summary = "Un-notified hearing matches",
            description = "Returns every HearingMatch where notified=false — the queue the dispatcher has not yet sent.")
    public ResponseEntity<ApiResponse<List<HearingMatchDto>>> unnotifiedMatches() {
        List<HearingMatch> matches = matchingService.findUnnotified();
        List<HearingMatchDto> dtos = matches.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return responseHandler.ok(dtos, "Un-notified matches fetched");
    }

    private HearingMatchDto toDto(HearingMatch m) {
        return new HearingMatchDto(
                m.getId(),
                m.getClientCaseId(),
                m.getCourtId(),
                m.getCaseNoInternal(),
                m.getHearingDateBs(),
                m.getHearingDateAd(),
                m.getBench(),
                m.getSerialNo(),
                m.getJudgeName(),
                m.getOrderType(),
                m.getSubject(),
                m.getPlaintiff(),
                m.getDefendant(),
                m.getCaseNoBs(),
                m.getSource().name(),
                m.getMatchedAt(),
                m.isNotified()
        );
    }

    // Simple DTO so we don't leak the entity's lazy/proxy shape.
    public record HearingMatchDto(
            Long id,
            Long clientCaseId,
            Integer courtId,
            String caseNoInternal,
            String hearingDateBs,
            LocalDate hearingDateAd,
            String bench,
            String serialNo,
            String judgeName,
            String orderType,
            String subject,
            String plaintiff,
            String defendant,
            String caseNoBs,
            String source,
            java.time.LocalDateTime matchedAt,
            boolean notified
    ) {}
}
