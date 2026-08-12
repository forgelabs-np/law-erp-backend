package com.lawfirm.erp.modules.casemanagement.controller;

import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.casemanagement.dto.request.*;
import com.lawfirm.erp.modules.casemanagement.dto.response.MatterResponse;
import com.lawfirm.erp.modules.casemanagement.dto.response.PartyMatchResult;
import com.lawfirm.erp.modules.casemanagement.dto.response.StaleMatterResponse;
import com.lawfirm.erp.modules.casemanagement.dto.response.TimelineEventResponse;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import com.lawfirm.erp.modules.casemanagement.service.MatterService;
import com.lawfirm.erp.modules.casemanagement.service.PartyMatchService;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.exception.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/firm/matters")
@RequiredArgsConstructor
@Tag(name = "Matters", description = "The dispute as the firm tracks it — owns a chain of CourtCases")
@PreAuthorize("hasAnyRole('FIRM_ADMIN', 'ADVOCATE', 'PARALEGAL')")
public class MatterController {

    private final MatterService matterService;
    private final PartyMatchService partyMatchService;
    private final CurrentUserResolver currentUserResolver;
    private final ResponseHandler responseHandler;

    @PostMapping
    @Operation(summary = "Create a matter", description = "Creates the Matter + ORIGINAL CourtCase at the originating court level, with optional parties")
    public ResponseEntity<ApiResponse<MatterResponse>> createMatter(
            @Valid @RequestBody ApiRequest<CreateMatterRequest> request) {
        return responseHandler.ok(matterService.createMatter(request.getData()),
                "Matter created successfully");
    }

    @GetMapping
    @Operation(summary = "List matters", description = "Paginated list with filters (matterType, status, search)")
    public ResponseEntity<ApiResponse<Page<MatterResponse>>> listMatters(
            @RequestParam(required = false) MatterType matterType,
            @RequestParam(required = false) MatterStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responseHandler.ok(
                matterService.listMatters(matterType, status, search, page, size),
                "Matters fetched successfully");
    }

    @GetMapping("/stale")
    @Operation(summary = "Stale matters", description = "Matters whose current leaf hasn't had a real Peshi in N days (long-pending Tarik chains)")
    public ResponseEntity<ApiResponse<Page<StaleMatterResponse>>> getStaleMatters(
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responseHandler.ok(
                matterService.getStaleMatters(days, page, size),
                "Stale matters fetched successfully");
    }

    @GetMapping("/{matterNumber}")
    @Operation(summary = "Get matter details", description = "Matter with full CourtCase chain, roles and parties. Use matterNumber (e.g. APX-MAT-2026-00001).")
    public ResponseEntity<ApiResponse<MatterResponse>> getMatter(@PathVariable String matterNumber) {
        return responseHandler.ok(matterService.getMatter(matterNumber),
                "Matter fetched successfully");
    }

    @PutMapping("/{matterNumber}")
    @Operation(summary = "Update matter details")
    public ResponseEntity<ApiResponse<MatterResponse>> updateMatter(
            @PathVariable String matterNumber,
            @Valid @RequestBody ApiRequest<UpdateMatterRequest> request) {
        return responseHandler.ok(matterService.updateMatter(matterNumber, request.getData()),
                "Matter updated successfully");
    }

    @GetMapping("/{matterNumber}/timeline")
    @Operation(summary = "Matter timeline", description = "Aggregated event history across all CourtCases, each tagged with its court case")
    public ResponseEntity<ApiResponse<List<TimelineEventResponse>>> getTimeline(
            @PathVariable String matterNumber) {
        return responseHandler.ok(matterService.getTimeline(matterNumber),
                "Timeline fetched successfully");
    }

    @GetMapping("/timeline")
    @Operation(summary = "Overall timeline", description = "Firm-wide activity feed across all matters, newest first — filters: matterType, status, from, to (YYYY-MM-DD)")
    public ResponseEntity<ApiResponse<Page<TimelineEventResponse>>> getFirmTimeline(
            @RequestParam(required = false) MatterType matterType,
            @RequestParam(required = false) MatterStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responseHandler.ok(
                matterService.getFirmTimeline(matterType, status, from, to, page, size),
                "Timeline fetched successfully");
    }

    @PostMapping("/{matterNumber}/court-cases")
    @Operation(summary = "Add court case", description = "Attach an appeal / remand / writ / review CourtCase to the matter chain")
    public ResponseEntity<ApiResponse<MatterResponse>> addCourtCase(
            @PathVariable String matterNumber,
            @Valid @RequestBody ApiRequest<AddCourtCaseRequest> request) {
        return responseHandler.ok(matterService.addCourtCase(matterNumber, request.getData()),
                "Court case added successfully");
    }

    @PostMapping("/{matterNumber}/parties")
    @Operation(summary = "Add party to matter")
    public ResponseEntity<ApiResponse<MatterResponse>> addParty(
            @PathVariable String matterNumber,
            @Valid @RequestBody ApiRequest<PartyEntryRequest> request) {
        return responseHandler.ok(matterService.addParty(matterNumber, request.getData()),
                "Party added successfully");
    }

    @PostMapping("/parties/match")
    @Operation(summary = "Match a party against existing clients and matter parties")
    public ResponseEntity<ApiResponse<List<PartyMatchResult.Match>>> matchParty(
            @Valid @RequestBody ApiRequest<PartyMatchRequest> request) {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return responseHandler.ok(
                partyMatchService.match(firmId,
                        request.getData().getFullName(),
                        request.getData().getMobileNo(),
                        request.getData().getEmail()),
                "Matches found");
    }
}
