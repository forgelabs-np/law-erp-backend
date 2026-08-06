package com.lawfirm.erp.modules.casemanagement.controller;

import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.casemanagement.dto.request.CreateHearingRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateHearingRequest;
import com.lawfirm.erp.modules.casemanagement.dto.response.HearingResponse;
import com.lawfirm.erp.modules.casemanagement.service.HearingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/firm")
@RequiredArgsConstructor
@Tag(name = "Case Hearings", description = "Manage hearings and court dates (tarikh)")
@PreAuthorize("hasAnyRole('FIRM_ADMIN', 'ADVOCATE', 'PARALEGAL')")
public class HearingController {

    private final HearingService hearingService;
    private final ResponseHandler responseHandler;

    @PostMapping("/cases/{caseNumber}/hearings")
    @Operation(summary = "Schedule a hearing", description = "Creates a hearing with conflict detection. caseNumber format: FIRMCODE-TYPE-YEAR-SEQ (e.g. APX-CIV-2026-00001)")
    public ResponseEntity<ApiResponse<HearingResponse>> scheduleHearing(
            @PathVariable String caseNumber,
            @Valid @RequestBody ApiRequest<CreateHearingRequest> request) {
        return responseHandler.ok(
                hearingService.scheduleHearing(caseNumber, request.getData()),
                "Hearing scheduled successfully");
    }

    @GetMapping("/cases/{caseNumber}/hearings")
    @Operation(summary = "List hearings for a case")
    public ResponseEntity<ApiResponse<List<HearingResponse>>> getCaseHearings(
            @PathVariable String caseNumber) {
        return responseHandler.ok(
                hearingService.getCaseHearings(caseNumber),
                "Hearings fetched successfully");
    }

    @GetMapping("/hearings/{hearingId}")
    @Operation(summary = "Get hearing details")
    public ResponseEntity<ApiResponse<HearingResponse>> getHearing(
            @PathVariable UUID hearingId) {
        return responseHandler.ok(
                hearingService.getHearing(hearingId),
                "Hearing fetched successfully");
    }

    @PutMapping("/hearings/{hearingId}")
    @Operation(summary = "Update hearing details")
    public ResponseEntity<ApiResponse<HearingResponse>> updateHearing(
            @PathVariable UUID hearingId,
            @Valid @RequestBody ApiRequest<UpdateHearingRequest> request) {
        return responseHandler.ok(
                hearingService.updateHearing(hearingId, request.getData()),
                "Hearing updated successfully");
    }

    @DeleteMapping("/hearings/{hearingId}")
    @Operation(summary = "Cancel a hearing")
    public ResponseEntity<ApiResponse<Void>> cancelHearing(@PathVariable UUID hearingId) {
        hearingService.cancelHearing(hearingId);
        return responseHandler.ok(null, "Hearing cancelled successfully");
    }
}
