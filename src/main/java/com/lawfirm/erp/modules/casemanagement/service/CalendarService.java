package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.modules.casemanagement.dto.response.CalendarEventResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CalendarService {

    List<CalendarEventResponse> getCalendar(UUID firmId, LocalDate from, LocalDate to, UUID advocateId);

    List<CalendarEventResponse> getTodayEvents(UUID firmId, UUID advocateId);

    List<CalendarEventResponse> getUpcomingEvents(UUID firmId, int days, UUID advocateId);
}
