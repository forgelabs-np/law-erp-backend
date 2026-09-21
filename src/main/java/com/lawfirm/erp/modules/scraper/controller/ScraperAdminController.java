package com.lawfirm.erp.modules.scraper.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.constant.ScraperConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.scraper.converter.NepaliDateUtil;
import com.lawfirm.erp.modules.scraper.dto.ScrapeRunResult;
import com.lawfirm.erp.modules.scraper.entity.Court;
import com.lawfirm.erp.modules.scraper.entity.ClientCase;
import com.lawfirm.erp.modules.scraper.service.HearingExportService;
import com.lawfirm.erp.modules.scraper.service.ScraperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/scraper/admin")
@RequiredArgsConstructor
@Tag(name = "Scraper Admin", description = "Manual scrape triggers, exports and court reference for backfill/testing")
public class ScraperAdminController {

    private final ScraperService scraperService;
    private final HearingExportService exportService;
    private final PermissionEvaluator permissionEvaluator;
    private final ResponseHandler responseHandler;

    @GetMapping("/courts")
    @Operation(summary = "All courts",
            description = "Returns every active court in the scraper database (district, high, supreme) with courtId, courtName and courtType.")
    public ResponseEntity<ApiResponse<List<CourtDto>>> getAllCourts() {
        List<Court> courts = scraperService.getAllCourts();
        List<CourtDto> dtos = courts.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return responseHandler.ok(dtos, "Courts fetched successfully");
    }

    @GetMapping("/courts/type/{courtType}")
    @Operation(summary = "Courts by type",
            description = "Returns courts filtered by type: DISTRICT, HIGH_COURT, SUPREME_COURT.")
    public ResponseEntity<ApiResponse<List<CourtDto>>> getCourtsByType(
            @PathVariable String courtType) {
        List<Court> courts = scraperService.getCourtsByType(courtType);
        List<CourtDto> dtos = courts.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return responseHandler.ok(dtos, "Courts fetched successfully");
    }

    @PostMapping("/scrape")
    @Operation(summary = ScraperConstants.SCRAPE_SUMMARY, description = ScraperConstants.SCRAPE_DESCRIPTION)
    public ResponseEntity<ApiResponse<ScrapeRunResult>> scrape(
            @RequestParam Integer courtId,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "daily") String mode) {
        permissionEvaluator.require("SCRAPER_MANAGEMENT:CREATE");
        if ("weekly".equalsIgnoreCase(mode)) {
            ScrapeRunResult result = scraperService.runWeeklyScrapeForCourt(courtId);
            return responseHandler.ok(result,
                    result.isSuccess() ? "Weekly scrape completed" : "Scrape failed — see result for details");
        }
        String dateBs = date != null ? date : NepaliDateUtil.adToBs(LocalDate.now());
        ScrapeRunResult result = scraperService.runDailyScrapeForCourt(courtId, dateBs);
        return responseHandler.ok(result,
                result.isSuccess() ? "Scrape completed" : "Scrape failed — see result for details");
    }

    @PostMapping("/export")
    @Operation(summary = ScraperConstants.EXPORT_SUMMARY, description = ScraperConstants.EXPORT_DESCRIPTION)
    public ResponseEntity<ApiResponse<String>> exportLastWeek() {
        permissionEvaluator.require("SCRAPER_MANAGEMENT:EXPORT");
        Path file = exportService.exportLastWeek();
        return responseHandler.ok(file.toAbsolutePath().toString(), "Weekly export written");
    }

    @PostMapping("/link-court-case")
    @Operation(summary = "Link court case to scraper",
            description = "Links a case management court case to the scraper for hearing tracking. Creates or updates the client case with the court's official number.")
    public ResponseEntity<ApiResponse<ClientCase>> linkCourtCase(
            @RequestBody Map<String, Object> request) {
        permissionEvaluator.require("SCRAPER_MANAGEMENT:CREATE");
        
        Object courtIdObj = request.get("courtId");
        Object courtCaseNumberObj = request.get("courtCaseNumber");
        Object caseNoInternalObj = request.get("caseNoInternal");
        Object clientIdObj = request.get("clientId");
        
        if (courtIdObj == null || courtCaseNumberObj == null || caseNoInternalObj == null || clientIdObj == null) {
            throw new BusinessRuleException("Missing required fields: courtId, courtCaseNumber, caseNoInternal, clientId");
        }
        
        Integer courtId;
        try {
            courtId = (courtIdObj instanceof Integer) ? (Integer) courtIdObj : Integer.valueOf(courtIdObj.toString());
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("courtId must be a valid integer");
        }
        
        String courtCaseNumber = courtCaseNumberObj.toString().trim();
        String caseNoInternal = caseNoInternalObj.toString().trim();
        
        if (courtCaseNumber.isBlank() || caseNoInternal.isBlank()) {
            throw new BusinessRuleException("courtCaseNumber and caseNoInternal must not be empty");
        }
        
        UUID clientId;
        try {
            clientId = UUID.fromString(clientIdObj.toString());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("clientId must be a valid UUID");
        }
        
        ClientCase clientCase = scraperService.linkCourtCaseToScraper(
                courtId, courtCaseNumber, caseNoInternal, clientId);
        
        return responseHandler.ok(clientCase, "Court case linked to scraper successfully");
    }

    @GetMapping("/client-cases/{clientId}")
    @Operation(summary = "Get client cases",
            description = "Returns all tracked cases for a specific client.")
    public ResponseEntity<ApiResponse<List<ClientCase>>> getClientCases(
            @PathVariable UUID clientId) {
        permissionEvaluator.require("SCRAPER_MANAGEMENT:VIEW");
        List<ClientCase> cases = scraperService.getClientCases(clientId);
        return responseHandler.ok(cases, "Client cases fetched successfully");
    }

    private CourtDto toDto(Court c) {
        return new CourtDto(c.getCourtId(), c.getCourtNameNepali(), c.getCourtNameEnglish(),
                c.getCourtType(), c.isActive());
    }

    public record CourtDto(Integer courtId, String courtNameNepali, String courtNameEnglish,
                           String courtType, boolean isActive) {}
}
