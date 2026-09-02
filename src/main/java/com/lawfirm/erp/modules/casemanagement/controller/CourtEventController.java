package com.lawfirm.erp.modules.casemanagement.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.constant.CaseManagementConstants;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.casemanagement.dto.request.MarkCourtEventHeldRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.ScheduleCourtEventRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateCourtEventRequest;
import com.lawfirm.erp.modules.casemanagement.dto.response.CourtEventResponse;
import com.lawfirm.erp.modules.casemanagement.service.CourtEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/firm")
@RequiredArgsConstructor
@Tag(name = "Court Events", description = "Tarik/Peshi loop — marking held creates the next event")
public class CourtEventController {

    private final CourtEventService courtEventService;
    private final PermissionEvaluator permissionEvaluator;
    private final ResponseHandler responseHandler;

    @PostMapping("/court-cases/{ourCourtCaseRef}/events")
    @Operation(summary = CaseManagementConstants.SCHEDULE_EVENT_SUMMARY, description = CaseManagementConstants.SCHEDULE_EVENT_DESCRIPTION)
    public ResponseEntity<ApiResponse<CourtEventResponse>> scheduleEvent(
            @PathVariable String ourCourtCaseRef,
            @Valid @RequestBody ApiRequest<ScheduleCourtEventRequest> request) {
        permissionEvaluator.require("CASE_MANAGEMENT:CREATE");
        return responseHandler.ok(courtEventService.scheduleEvent(ourCourtCaseRef, request.getData()),
                "Event scheduled successfully");
    }

    @GetMapping("/court-cases/{ourCourtCaseRef}/events")
    @Operation(summary = CaseManagementConstants.LIST_EVENTS_SUMMARY, description = CaseManagementConstants.LIST_EVENTS_DESCRIPTION)
    public ResponseEntity<ApiResponse<List<CourtEventResponse>>> listEvents(
            @PathVariable String ourCourtCaseRef) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
        return responseHandler.ok(courtEventService.listEvents(ourCourtCaseRef),
                "Events fetched successfully");
    }

    @GetMapping("/court-events/{eventId}")
    @Operation(summary = CaseManagementConstants.GET_EVENT_SUMMARY)
    public ResponseEntity<ApiResponse<CourtEventResponse>> getEvent(@PathVariable UUID eventId) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
        return responseHandler.ok(courtEventService.getEvent(eventId),
                "Event fetched successfully");
    }

    @PutMapping("/court-events/{eventId}")
    @Operation(summary = CaseManagementConstants.UPDATE_EVENT_SUMMARY)
    public ResponseEntity<ApiResponse<CourtEventResponse>> updateEvent(
            @PathVariable UUID eventId,
            @Valid @RequestBody ApiRequest<UpdateCourtEventRequest> request) {
        permissionEvaluator.require("CASE_MANAGEMENT:EDIT");
        return responseHandler.ok(courtEventService.updateEvent(eventId, request.getData()),
                "Event updated successfully");
    }

    @PostMapping("/court-events/{eventId}/held")
    @Operation(summary = CaseManagementConstants.MARK_HELD_SUMMARY, description = CaseManagementConstants.MARK_HELD_DESCRIPTION)
    public ResponseEntity<ApiResponse<CourtEventResponse>> markHeld(
            @PathVariable UUID eventId,
            @Valid @RequestBody ApiRequest<MarkCourtEventHeldRequest> request) {
        permissionEvaluator.require("CASE_MANAGEMENT:EDIT");
        return responseHandler.ok(courtEventService.markHeld(eventId, request.getData()),
                "Event marked held successfully");
    }

    @DeleteMapping("/court-events/{eventId}")
    @Operation(summary = CaseManagementConstants.CANCEL_EVENT_SUMMARY)
    public ResponseEntity<ApiResponse<Void>> cancelEvent(@PathVariable UUID eventId) {
        permissionEvaluator.require("CASE_MANAGEMENT:DELETE");
        courtEventService.cancelEvent(eventId);
        return responseHandler.ok(null, "Event cancelled successfully");
    }
}
