package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.modules.casemanagement.dto.request.CreateHearingRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateHearingRequest;
import com.lawfirm.erp.modules.casemanagement.dto.response.HearingResponse;
import com.lawfirm.erp.modules.casemanagement.entity.Case;
import com.lawfirm.erp.modules.casemanagement.entity.CaseTimelineEvent;
import com.lawfirm.erp.modules.casemanagement.entity.Hearing;
import com.lawfirm.erp.modules.casemanagement.enums.HearingStatus;
import com.lawfirm.erp.modules.casemanagement.enums.TimelineEventType;
import com.lawfirm.erp.modules.casemanagement.repository.CaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CaseTimelineRepository;
import com.lawfirm.erp.modules.casemanagement.repository.HearingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HearingService {

    private final HearingRepository hearingRepository;
    private final CaseRepository caseRepository;
    private final CaseTimelineRepository caseTimelineRepository;
    private final AuditService auditService;

    @Transactional
    public HearingResponse scheduleHearing(String caseNumber, CreateHearingRequest request) {
        UUID firmId = getRequiredFirmId();

        Case c = caseRepository.findByCaseNumberAndFirmId(caseNumber, firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + caseNumber));

        // Conflict detection
        if (request.getAdvocateId() != null && request.getTime() != null && request.getEndTime() != null) {
            List<Hearing> conflicts = hearingRepository.findConflicts(
                    request.getAdvocateId(), request.getDate(),
                    request.getTime(), request.getEndTime(), null);
            if (!conflicts.isEmpty()) {
                throw new BusinessRuleException(
                        "Advocate has a scheduling conflict at the given time");
            }
        }

        Hearing h = new Hearing();
        h.setFirmId(firmId);
        h.setCaseId(c.getId());
        h.setTitle(request.getTitle());
        h.setDate(request.getDate());
        h.setTime(request.getTime());
        h.setEndTime(request.getEndTime());
        h.setCourtRoom(request.getCourtRoom());
        h.setJudgeName(request.getJudgeName());
        h.setHearingType(request.getHearingType());
        h.setStatus(HearingStatus.SCHEDULED);
        h.setNotes(request.getNotes());
        h.setAttendees(request.getAttendees());
        h.setAdvocateId(request.getAdvocateId());

        Hearing saved = hearingRepository.save(h);

        // Record in timeline
        recordTimeline(c, TimelineEventType.HEARING_SCHEDULED,
                "Hearing scheduled: " + request.getTitle(), null);

        // Audit log
        auditService.log(AuditAction.HEARING_SCHEDULED, AuditEntity.HEARING, saved.getId(),
                "Hearing scheduled: " + request.getTitle() + " for case " + caseNumber);

        return toResponse(saved);
    }

    public List<HearingResponse> getCaseHearings(String caseNumber) {
        UUID firmId = getRequiredFirmId();

        Case c = caseRepository.findByCaseNumberAndFirmId(caseNumber, firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + caseNumber));

        return hearingRepository.findByCaseIdOrderByDateDesc(c.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public HearingResponse getHearing(UUID hearingId) {
        UUID firmId = getRequiredFirmId();
        Hearing h = hearingRepository.findByIdAndFirmId(hearingId, firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Hearing not found: " + hearingId));
        return toResponse(h);
    }

    @Transactional
    public HearingResponse updateHearing(UUID hearingId, UpdateHearingRequest request) {
        UUID firmId = getRequiredFirmId();
        Hearing h = hearingRepository.findByIdAndFirmId(hearingId, firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Hearing not found: " + hearingId));

        if (request.getTitle() != null) h.setTitle(request.getTitle());
        if (request.getDate() != null) h.setDate(request.getDate());
        if (request.getTime() != null) h.setTime(request.getTime());
        if (request.getEndTime() != null) h.setEndTime(request.getEndTime());
        if (request.getCourtRoom() != null) h.setCourtRoom(request.getCourtRoom());
        if (request.getJudgeName() != null) h.setJudgeName(request.getJudgeName());
        if (request.getHearingType() != null) h.setHearingType(request.getHearingType());
        if (request.getOutcome() != null) h.setOutcome(request.getOutcome());
        if (request.getNotes() != null) h.setNotes(request.getNotes());
        if (request.getAttendees() != null) h.setAttendees(request.getAttendees());
        if (request.getAdvocateId() != null) h.setAdvocateId(request.getAdvocateId());

        if (request.getStatus() != null) {
            HearingStatus oldStatus = h.getStatus();
            h.setStatus(request.getStatus());

            // Record timeline events for status changes
            Case c = caseRepository.findByIdAndFirmId(h.getCaseId(), firmId)
                    .orElseThrow(() -> new ResourceNotFoundException("Case not found"));

            if (request.getStatus() == HearingStatus.HELD && oldStatus != HearingStatus.HELD) {
                recordTimeline(c, TimelineEventType.HEARING_HELD,
                        "Hearing held: " + h.getTitle(),
                        request.getOutcome());
            } else if (request.getStatus() == HearingStatus.ADJOURNED && oldStatus != HearingStatus.ADJOURNED) {
                recordTimeline(c, TimelineEventType.HEARING_ADJOURNED,
                        "Hearing adjourned: " + h.getTitle(), null);
            }
        }

        Hearing saved = hearingRepository.save(h);

        // Audit log
        auditService.log(AuditAction.HEARING_UPDATED, AuditEntity.HEARING, saved.getId(),
                "Hearing updated: " + (saved.getTitle() != null ? saved.getTitle() : ""));

        return toResponse(saved);
    }

    @Transactional
    public void cancelHearing(UUID hearingId) {
        UUID firmId = getRequiredFirmId();
        Hearing h = hearingRepository.findByIdAndFirmId(hearingId, firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Hearing not found: " + hearingId));

        h.setStatus(HearingStatus.CANCELED);
        hearingRepository.save(h);

        // Audit log
        auditService.log(AuditAction.HEARING_CANCELLED, AuditEntity.HEARING, hearingId,
                "Hearing cancelled: " + (h.getTitle() != null ? h.getTitle() : ""));
    }

    private UUID getRequiredFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }

    private HearingResponse toResponse(Hearing h) {
        return HearingResponse.builder()
                .id(h.getId())
                .caseId(h.getCaseId())
                .title(h.getTitle())
                .date(h.getDate())
                .time(h.getTime())
                .endTime(h.getEndTime())
                .courtRoom(h.getCourtRoom())
                .judgeName(h.getJudgeName())
                .hearingType(h.getHearingType())
                .status(h.getStatus())
                .outcome(h.getOutcome())
                .notes(h.getNotes())
                .attendees(h.getAttendees())
                .advocateId(h.getAdvocateId())
                .createdAt(h.getCreatedAt())
                .build();
    }

    private void recordTimeline(Case c, TimelineEventType type, String title, String description) {
        CaseTimelineEvent event = new CaseTimelineEvent();
        event.setFirmId(c.getFirmId());
        event.setCaseId(c.getId());
        event.setEventType(type);
        event.setTitle(title);
        event.setDescription(description);
        caseTimelineRepository.save(event);
    }
}
