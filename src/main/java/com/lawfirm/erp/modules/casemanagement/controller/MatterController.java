package com.lawfirm.erp.modules.casemanagement.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.constant.CaseManagementConstants;
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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/firm/matters")
@RequiredArgsConstructor
@Tag(name = "Matters", description = "The dispute as the firm tracks it — owns a chain of CourtCases")
public class MatterController {

    private final MatterService matterService;
    private final PartyMatchService partyMatchService;
    private final CurrentUserResolver currentUserResolver;
    private final PermissionEvaluator permissionEvaluator;
    private final ResponseHandler responseHandler;

    @PostMapping
    @Operation(summary = CaseManagementConstants.CREATE_MATTER_SUMMARY, description = CaseManagementConstants.CREATE_MATTER_DESCRIPTION)
    public ResponseEntity<ApiResponse<MatterResponse>> createMatter(
            @Valid @RequestBody ApiRequest<CreateMatterRequest> request) {
        permissionEvaluator.require("CASE_MANAGEMENT:CREATE");
        return responseHandler.ok(matterService.createMatter(request.getData()),
                "Matter created successfully");
    }

    @GetMapping
    @Operation(summary = CaseManagementConstants.LIST_MATTERS_SUMMARY, description = CaseManagementConstants.LIST_MATTERS_DESCRIPTION)
    public ResponseEntity<ApiResponse<Page<MatterResponse>>> listMatters(
            @RequestParam(required = false) MatterType matterType,
            @RequestParam(required = false) MatterStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
        return responseHandler.ok(
                matterService.listMatters(matterType, status, search, page, size),
                "Matters fetched successfully");
    }

    @GetMapping("/stale")
    @Operation(summary = CaseManagementConstants.GET_STALE_MATTERS_SUMMARY, description = CaseManagementConstants.GET_STALE_MATTERS_DESCRIPTION)
    public ResponseEntity<ApiResponse<Page<StaleMatterResponse>>> getStaleMatters(
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
        return responseHandler.ok(
                matterService.getStaleMatters(days, page, size),
                "Stale matters fetched successfully");
    }

    @GetMapping("/{matterNumber}")
    @Operation(summary = CaseManagementConstants.GET_MATTER_SUMMARY, description = CaseManagementConstants.GET_MATTER_DESCRIPTION)
    public ResponseEntity<ApiResponse<MatterResponse>> getMatter(@PathVariable String matterNumber) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
        return responseHandler.ok(matterService.getMatter(matterNumber),
                "Matter fetched successfully");
    }

    @PutMapping("/{matterNumber}")
    @Operation(summary = CaseManagementConstants.UPDATE_MATTER_SUMMARY)
    public ResponseEntity<ApiResponse<MatterResponse>> updateMatter(
            @PathVariable String matterNumber,
            @Valid @RequestBody ApiRequest<UpdateMatterRequest> request) {
        permissionEvaluator.require("CASE_MANAGEMENT:EDIT");
        return responseHandler.ok(matterService.updateMatter(matterNumber, request.getData()),
                "Matter updated successfully");
    }

    @GetMapping("/{matterNumber}/timeline")
    @Operation(summary = CaseManagementConstants.GET_TIMELINE_SUMMARY, description = CaseManagementConstants.GET_TIMELINE_DESCRIPTION)
    public ResponseEntity<ApiResponse<List<TimelineEventResponse>>> getTimeline(
            @PathVariable String matterNumber) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
        return responseHandler.ok(matterService.getTimeline(matterNumber),
                "Timeline fetched successfully");
    }

    @GetMapping("/timeline")
    @Operation(summary = CaseManagementConstants.GET_FIRM_TIMELINE_SUMMARY, description = CaseManagementConstants.GET_FIRM_TIMELINE_DESCRIPTION)
    public ResponseEntity<ApiResponse<Page<TimelineEventResponse>>> getFirmTimeline(
            @RequestParam(required = false) MatterType matterType,
            @RequestParam(required = false) MatterStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
        return responseHandler.ok(
                matterService.getFirmTimeline(matterType, status, from, to, page, size),
                "Timeline fetched successfully");
    }

    @PostMapping("/{matterNumber}/court-cases")
    @Operation(summary = CaseManagementConstants.ADD_COURT_CASE_SUMMARY, description = CaseManagementConstants.ADD_COURT_CASE_DESCRIPTION)
    public ResponseEntity<ApiResponse<MatterResponse>> addCourtCase(
            @PathVariable String matterNumber,
            @Valid @RequestBody ApiRequest<AddCourtCaseRequest> request) {
        permissionEvaluator.require("CASE_MANAGEMENT:CREATE");
        return responseHandler.ok(matterService.addCourtCase(matterNumber, request.getData()),
                "Court case added successfully");
    }

    @PostMapping("/{matterNumber}/parties")
    @Operation(summary = CaseManagementConstants.ADD_PARTY_SUMMARY)
    public ResponseEntity<ApiResponse<MatterResponse>> addParty(
            @PathVariable String matterNumber,
            @Valid @RequestBody ApiRequest<PartyEntryRequest> request) {
        permissionEvaluator.require("CASE_MANAGEMENT:CREATE");
        return responseHandler.ok(matterService.addParty(matterNumber, request.getData()),
                "Party added successfully");
    }

    @PostMapping("/parties/match")
    @Operation(summary = CaseManagementConstants.MATCH_PARTY_SUMMARY)
    public ResponseEntity<ApiResponse<List<PartyMatchResult.Match>>> matchParty(
            @Valid @RequestBody ApiRequest<PartyMatchRequest> request) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
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
