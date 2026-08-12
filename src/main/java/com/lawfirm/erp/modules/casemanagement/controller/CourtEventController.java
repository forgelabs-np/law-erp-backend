package com.lawfirm.erp.modules.casemanagement.controller;

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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/firm")
@RequiredArgsConstructor
@Tag(name = "Court Events", description = "Tarik/Peshi loop — marking held creates the next event")
@PreAuthorize("hasAnyRole('FIRM_ADMIN', 'ADVOCATE', 'PARALEGAL')")
public class CourtEventController {

    private final CourtEventService courtEventService;
    private final ResponseHandler responseHandler;

    @PostMapping("/court-cases/{ourCourtCaseRef}/events")
    @Operation(summary = "Schedule a Tarik/Peshi event", description = "Conflict detection against all of the advocate's events firm-wide")
    public ResponseEntity<ApiResponse<CourtEventResponse>> scheduleEvent(
            @PathVariable String ourCourtCaseRef,
            @Valid @RequestBody ApiRequest<ScheduleCourtEventRequest> request) {
        return responseHandler.ok(courtEventService.scheduleEvent(ourCourtCaseRef, request.getData()),
                "Event scheduled successfully");
    }

    @GetMapping("/court-cases/{ourCourtCaseRef}/events")
    @Operation(summary = "List events for a court case", description = "Chained Tarik/Peshi stream, ordered by sequence")
    public ResponseEntity<ApiResponse<List<CourtEventResponse>>> listEvents(
            @PathVariable String ourCourtCaseRef) {
        return responseHandler.ok(courtEventService.listEvents(ourCourtCaseRef),
                "Events fetched successfully");
    }

    @GetMapping("/court-events/{eventId}")
    @Operation(summary = "Get event details")
    public ResponseEntity<ApiResponse<CourtEventResponse>> getEvent(@PathVariable UUID eventId) {
        return responseHandler.ok(courtEventService.getEvent(eventId),
                "Event fetched successfully");
    }

    @PutMapping("/court-events/{eventId}")
    @Operation(summary = "Update event details")
    public ResponseEntity<ApiResponse<CourtEventResponse>> updateEvent(
            @PathVariable UUID eventId,
            @Valid @RequestBody ApiRequest<UpdateCourtEventRequest> request) {
        return responseHandler.ok(courtEventService.updateEvent(eventId, request.getData()),
                "Event updated successfully");
    }

    @PostMapping("/court-events/{eventId}/held")
    @Operation(summary = "Mark event held", description = "Records outcome and creates the next Tarik/Peshi event the court gave")
    public ResponseEntity<ApiResponse<CourtEventResponse>> markHeld(
            @PathVariable UUID eventId,
            @Valid @RequestBody ApiRequest<MarkCourtEventHeldRequest> request) {
        return responseHandler.ok(courtEventService.markHeld(eventId, request.getData()),
                "Event marked held successfully");
    }

    @DeleteMapping("/court-events/{eventId}")
    @Operation(summary = "Cancel an event")
    public ResponseEntity<ApiResponse<Void>> cancelEvent(@PathVariable UUID eventId) {
        courtEventService.cancelEvent(eventId);
        return responseHandler.ok(null, "Event cancelled successfully");
    }
}
