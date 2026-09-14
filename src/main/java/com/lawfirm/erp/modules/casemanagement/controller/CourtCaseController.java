package com.lawfirm.erp.modules.casemanagement.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.casemanagement.dto.request.RecordJudgmentRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateCourtCaseRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateCourtCaseStageRequest;
import com.lawfirm.erp.modules.casemanagement.dto.response.CourtCaseResponse;
import com.lawfirm.erp.modules.casemanagement.dto.response.UpcomingAppealResponse;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStage;
import com.lawfirm.erp.modules.casemanagement.service.CourtCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/firm/court-cases")
@RequiredArgsConstructor
@Tag(name = "Court Cases", description = "One row per court instance the matter is registered in")
public class CourtCaseController {

    private final CourtCaseService courtCaseService;
    private final PermissionEvaluator permissionEvaluator;
    private final ResponseHandler responseHandler;

    @GetMapping("/{ourCourtCaseRef}")
    @Operation(summary = "Get court case details", description = "Use ourCourtCaseRef (e.g. APX-MAT-2026-00001-DC1)")
    public ResponseEntity<ApiResponse<CourtCaseResponse>> getCourtCase(
            @PathVariable String ourCourtCaseRef) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
        return responseHandler.ok(courtCaseService.getCourtCase(ourCourtCaseRef),
                "Court case fetched successfully");
    }

    @PutMapping("/{ourCourtCaseRef}")
    @Operation(summary = "Update court case details")
    public ResponseEntity<ApiResponse<CourtCaseResponse>> updateCourtCase(
            @PathVariable String ourCourtCaseRef,
            @Valid @RequestBody ApiRequest<UpdateCourtCaseRequest> request) {
        permissionEvaluator.require("CASE_MANAGEMENT:EDIT");
        return responseHandler.ok(courtCaseService.updateCourtCase(ourCourtCaseRef, request.getData()),
                "Court case updated successfully");
    }

    @PutMapping("/{ourCourtCaseRef}/stage")
    @Operation(summary = "Update court case stage", description = "Validates the transition against the court-level/type state machine")
    public ResponseEntity<ApiResponse<CourtCaseResponse>> updateStage(
            @PathVariable String ourCourtCaseRef,
            @Valid @RequestBody ApiRequest<UpdateCourtCaseStageRequest> request) {
        permissionEvaluator.require("CASE_MANAGEMENT:EDIT");
        return responseHandler.ok(courtCaseService.updateStage(ourCourtCaseRef, request.getData()),
                "Stage updated successfully");
    }

    @PutMapping("/{ourCourtCaseRef}/judgment")
    @Operation(summary = "Record judgment", description = "Sets judgment details, DECIDED status, and computes the statutory appeal deadline")
    public ResponseEntity<ApiResponse<CourtCaseResponse>> recordJudgment(
            @PathVariable String ourCourtCaseRef,
            @Valid @RequestBody ApiRequest<RecordJudgmentRequest> request) {
        permissionEvaluator.require("CASE_MANAGEMENT:EDIT");
        return responseHandler.ok(courtCaseService.recordJudgment(ourCourtCaseRef, request.getData()),
                "Judgment recorded successfully");
    }

    @GetMapping("/appeal-deadlines")
    @Operation(summary = "Upcoming appeal deadlines", description = "Decided cases whose statutory appeal window closes within N days (default 30) and no appeal has been filed — the watch list")
    public ResponseEntity<ApiResponse<List<UpcomingAppealResponse>>> upcomingAppealDeadlines(
            @RequestParam(defaultValue = "30") int withinDays) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
        return responseHandler.ok(courtCaseService.listUpcomingAppealDeadlines(withinDays),
                "Upcoming appeal deadlines fetched successfully");
    }

    @GetMapping("/{ourCourtCaseRef}/allowed-stages")
    @Operation(summary = "Allowed next stages", description = "Legal transitions from the current stage, filtered by court level / matter type / relation — so the UI never offers an illegal move")
    public ResponseEntity<ApiResponse<List<CourtCaseStage>>> allowedStages(
            @PathVariable String ourCourtCaseRef) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
        return responseHandler.ok(courtCaseService.getAllowedStages(ourCourtCaseRef),
                "Allowed stages fetched successfully");
    }
}
