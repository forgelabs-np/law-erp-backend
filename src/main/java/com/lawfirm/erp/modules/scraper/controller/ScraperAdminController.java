package com.lawfirm.erp.modules.scraper.controller;

import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.scraper.dto.ScrapeRunResult;
import com.lawfirm.erp.modules.scraper.service.HearingExportService;
import com.lawfirm.erp.modules.scraper.service.ScraperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/scraper/admin")
@RequiredArgsConstructor
@Tag(name = "Scraper Admin", description = "Manual scrape triggers and exports for backfill/testing")
@PreAuthorize("hasAnyRole('FIRM_ADMIN')")
public class ScraperAdminController {

    private final ScraperService scraperService;
    private final HearingExportService exportService;
    private final ResponseHandler responseHandler;

    @PostMapping("/scrape")
    @Operation(summary = "Trigger a daily scrape for one court",
            description = "Hits the court site live for a single court. date is BS yyyy-mm-dd (defaults to today).")
    public ResponseEntity<ApiResponse<ScrapeRunResult>> scrape(
            @RequestParam Integer courtId,
            @RequestParam(required = false) String date) {
        String dateBs = date != null ? date
                : com.lawfirm.erp.modules.scraper.converter.NepaliDateUtil.adToBs(java.time.LocalDate.now());
        ScrapeRunResult result = scraperService.runDailyScrapeForCourt(courtId, dateBs);
        return responseHandler.ok(result,
                result.isSuccess() ? "Scrape completed" : "Scrape failed — see result for details");
    }

    @PostMapping("/export")
    @Operation(summary = "Regenerate the weekly CSV export",
            description = "Re-exports the previous completed Monday–Sunday window to <export-dir>/week-<monday>_<sunday>.csv (overwrites).")
    public ResponseEntity<ApiResponse<String>> exportLastWeek() {
        Path file = exportService.exportLastWeek();
        return responseHandler.ok(file.toAbsolutePath().toString(), "Weekly export written");
    }
}
