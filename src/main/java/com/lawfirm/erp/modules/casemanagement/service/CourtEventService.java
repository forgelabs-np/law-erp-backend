package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.modules.casemanagement.dto.request.MarkCourtEventHeldRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.ScheduleCourtEventRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateCourtEventRequest;
import com.lawfirm.erp.modules.casemanagement.dto.response.CourtEventResponse;

import java.util.List;
import java.util.UUID;

public interface CourtEventService {

    CourtEventResponse scheduleEvent(String ourCourtCaseRef, ScheduleCourtEventRequest request);

    List<CourtEventResponse> listEvents(String ourCourtCaseRef);

    CourtEventResponse getEvent(UUID eventId);

    CourtEventResponse updateEvent(UUID eventId, UpdateCourtEventRequest request);

    CourtEventResponse markHeld(UUID eventId, MarkCourtEventHeldRequest request);

    void cancelEvent(UUID eventId);
}
