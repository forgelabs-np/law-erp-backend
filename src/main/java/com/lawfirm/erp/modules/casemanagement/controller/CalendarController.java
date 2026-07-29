package com.lawfirm.erp.modules.casemanagement.controller;

import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.casemanagement.dto.response.CalendarEventResponse;
import com.lawfirm.erp.modules.casemanagement.service.CalendarService;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/firm/calendar")
@RequiredArgsConstructor
@Tag(name = "Calendar", description = "Court calendar — hearings as the source of truth")
@PreAuthorize("hasAnyRole('FIRM_ADMIN', 'ADVOCATE', 'PARALEGAL')")
public class CalendarController {

    private final CalendarService calendarService;
    private final CurrentUserResolver currentUserResolver;
    private final ResponseHandler responseHandler;

    @GetMapping
    @Operation(summary = "Get calendar events", description = "Hearings in a date range, optionally filtered by advocate")
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID advocateId) {
        UUID firmId = getRequiredFirmId();
        return responseHandler.ok(
                calendarService.getCalendar(firmId, from, to, advocateId),
                "Calendar fetched successfully");
    }

    @GetMapping("/today")
    @Operation(summary = "Today's hearings", description = "All hearings scheduled for today, optionally filtered by advocate")
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getTodayHearings(
            @RequestParam(required = false) UUID advocateId) {
        UUID firmId = getRequiredFirmId();
        return responseHandler.ok(
                calendarService.getTodayHearings(firmId, advocateId),
                "Today's hearings fetched successfully");
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Upcoming hearings", description = "Hearings in the next N days (default 7)")
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getUpcomingHearings(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) UUID advocateId) {
        UUID firmId = getRequiredFirmId();
        return responseHandler.ok(
                calendarService.getUpcomingHearings(firmId, days, advocateId),
                "Upcoming hearings fetched successfully");
    }

    private UUID getRequiredFirmId() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }
}
