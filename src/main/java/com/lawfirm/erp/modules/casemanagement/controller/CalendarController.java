package com.lawfirm.erp.modules.casemanagement.controller;

import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.casemanagement.dto.response.CalendarEventResponse;
import com.lawfirm.erp.modules.casemanagement.service.CalendarService;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
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
    @Operation(summary = "Get calendar events", description = "Hearings in a date range, optionally filtered by advocate. " +
            "from/to are optional — defaults to last 30 days through next 90 days. " +
            "Accepts YYYY-MM-DD or full ISO datetime (e.g. 2026-08-01T00:00:00.000Z).")
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getCalendar(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID advocateId) {
        UUID firmId = getRequiredFirmId();

        LocalDate fromDate = parseDate(from, LocalDate.now().minusDays(30));
        LocalDate toDate = parseDate(to, LocalDate.now().plusDays(90));

        if (fromDate.isAfter(toDate)) {
            throw new BusinessRuleException("'from' date cannot be after 'to' date");
        }

        return responseHandler.ok(
                calendarService.getCalendar(firmId, fromDate, toDate, advocateId),
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
    @Operation(summary = "Upcoming hearings", description = "Hearings in the next N days (default 7, max 365)")
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getUpcomingHearings(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) UUID advocateId) {
        UUID firmId = getRequiredFirmId();

        if (days < 1 || days > 365) {
            throw new BusinessRuleException("'days' must be between 1 and 365");
        }

        return responseHandler.ok(
                calendarService.getUpcomingHearings(firmId, days, advocateId),
                "Upcoming hearings fetched successfully");
    }

    private UUID getRequiredFirmId() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }

    /**
     * Parses a date query parameter leniently so the frontend can send either:
     *   - plain date:        2026-08-01
     *   - ISO datetime:      2026-08-01T10:00:00
     *   - ISO instant:       2026-08-01T10:00:00.000Z  /  +05:45 offsets
     * When the parameter is absent or blank, returns the fallback value.
     */
    private LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String v = value.trim();

        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(v).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(v).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(v).atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        throw new BusinessRuleException(
                "Invalid date format for '" + v + "'. Expected YYYY-MM-DD or ISO datetime");
    }
}
