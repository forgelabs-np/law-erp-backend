package com.lawfirm.erp.modules.casemanagement.controller;

import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.casemanagement.dto.request.*;
import com.lawfirm.erp.modules.casemanagement.dto.response.CaseResponse;
import com.lawfirm.erp.modules.casemanagement.dto.response.PartyResponse;
import com.lawfirm.erp.modules.casemanagement.dto.response.PartyMatchResult;
import com.lawfirm.erp.modules.casemanagement.dto.response.TimelineEventResponse;
import com.lawfirm.erp.modules.casemanagement.enums.CaseStage;
import com.lawfirm.erp.modules.casemanagement.enums.CaseStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CaseType;
import com.lawfirm.erp.modules.casemanagement.service.CaseService;
import com.lawfirm.erp.modules.casemanagement.service.PartyMatchService;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/firm/cases")
@RequiredArgsConstructor
@Tag(name = "Case Management", description = "Manage legal cases, parties, and stage transitions")
@PreAuthorize("hasAnyRole('FIRM_ADMIN', 'ADVOCATE', 'PARALEGAL')")
public class CaseController {

    private final CaseService caseService;
    private final PartyMatchService partyMatchService;
    private final CurrentUserResolver currentUserResolver;
    private final ResponseHandler responseHandler;

    @PostMapping
    @Operation(summary = "Create a new case", description = "Creates case with optional parties. Returns party matches for dedup.")
    public ResponseEntity<ApiResponse<CaseResponse>> createCase(
            @Valid @RequestBody ApiRequest<CreateCaseRequest> request) {
        return responseHandler.ok(
                caseService.createCase(request.getData()),
                "Case created successfully");
    }

    @GetMapping
    @Operation(summary = "List cases", description = "Paginated list with filters (caseType, stage, status, assignedTo, date range, search)")
    public ResponseEntity<ApiResponse<Page<CaseResponse>>> listCases(
            @RequestParam(required = false) CaseType caseType,
            @RequestParam(required = false) CaseStage caseStage,
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) String courtName,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return responseHandler.ok(
                caseService.listCases(caseType, caseStage, status, assignedTo,
                        courtName, dateFrom, dateTo, search, page, size),
                "Cases fetched successfully");
    }

    @GetMapping("/{caseNumber}")
    @Operation(summary = "Get case details", description = "Returns case with parties and timeline summary. Use caseNumber (e.g. APX-CIV-2026-00001) as identifier.")
    public ResponseEntity<ApiResponse<CaseResponse>> getCase(@PathVariable String caseNumber) {
        return responseHandler.ok(
                caseService.getCase(caseNumber),
                "Case fetched successfully");
    }

    @PutMapping("/{caseNumber}")
    @Operation(summary = "Update case details")
    public ResponseEntity<ApiResponse<CaseResponse>> updateCase(
            @PathVariable String caseNumber,
            @Valid @RequestBody ApiRequest<UpdateCaseRequest> request) {
        return responseHandler.ok(
                caseService.updateCase(caseNumber, request.getData()),
                "Case updated successfully");
    }

    @DeleteMapping("/{caseNumber}")
    @Operation(summary = "Delete a case")
    public ResponseEntity<ApiResponse<Void>> deleteCase(@PathVariable String caseNumber) {
        caseService.deleteCase(caseNumber);
        return responseHandler.ok(null, "Case deleted successfully");
    }

    @PutMapping("/{caseNumber}/stage")
    @Operation(summary = "Update case stage", description = "Validates stage transition per case type lifecycle")
    public ResponseEntity<ApiResponse<CaseResponse>> updateStage(
            @PathVariable String caseNumber,
            @Valid @RequestBody ApiRequest<UpdateCaseStageRequest> request) {
        return responseHandler.ok(
                caseService.updateStage(caseNumber, request.getData()),
                "Stage updated successfully");
    }

    @GetMapping("/{caseNumber}/timeline")
    @Operation(summary = "Get case timeline", description = "Full event history for the case")
    public ResponseEntity<ApiResponse<List<TimelineEventResponse>>> getTimeline(
            @PathVariable String caseNumber) {
        return responseHandler.ok(
                caseService.getTimeline(caseNumber),
                "Timeline fetched successfully");
    }

    @PostMapping("/{caseNumber}/parties")
    @Operation(summary = "Add party to case")
    public ResponseEntity<ApiResponse<PartyResponse>> addParty(
            @PathVariable String caseNumber,
            @Valid @RequestBody ApiRequest<AddPartyRequest> request) {
        return responseHandler.ok(
                caseService.addParty(caseNumber, request.getData()),
                "Party added successfully");
    }

    @PutMapping("/{caseNumber}/parties/{partyId}")
    @Operation(summary = "Update party details")
    public ResponseEntity<ApiResponse<PartyResponse>> updateParty(
            @PathVariable String caseNumber,
            @PathVariable UUID partyId,
            @Valid @RequestBody ApiRequest<AddPartyRequest> request) {
        caseService.removeParty(caseNumber, partyId);
        return responseHandler.ok(
                caseService.addParty(caseNumber, request.getData()),
                "Party updated successfully");
    }

    @PutMapping("/{caseNumber}/parties/{partyId}/link")
    @Operation(summary = "Link party to an existing client record")
    public ResponseEntity<ApiResponse<PartyResponse>> linkParty(
            @PathVariable String caseNumber,
            @PathVariable UUID partyId,
            @Valid @RequestBody ApiRequest<LinkPartyRequest> request) {
        return responseHandler.ok(
                caseService.linkParty(caseNumber, partyId, request.getData()),
                "Party linked successfully");
    }

    @DeleteMapping("/{caseNumber}/parties/{partyId}")
    @Operation(summary = "Remove party from case")
    public ResponseEntity<ApiResponse<Void>> removeParty(
            @PathVariable String caseNumber,
            @PathVariable UUID partyId) {
        caseService.removeParty(caseNumber, partyId);
        return responseHandler.ok(null, "Party removed successfully");
    }

    private UUID getRequiredFirmId() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }

    @PostMapping("/parties/match")
    @Operation(summary = "Match a party against existing clients and case parties")
    public ResponseEntity<ApiResponse<List<PartyMatchResult.Match>>> matchParty(
            @Valid @RequestBody ApiRequest<PartyMatchRequest> request) {
        UUID firmId = getRequiredFirmId();
        List<PartyMatchResult.Match> matches = partyMatchService.match(
                firmId,
                request.getData().getFullName(),
                request.getData().getMobileNo(),
                request.getData().getEmail());
        return responseHandler.ok(matches, "Matches found");
    }
}
