package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.casemanagement.dto.request.MarkCourtEventHeldRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.ScheduleCourtEventRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateCourtEventRequest;
import com.lawfirm.erp.modules.casemanagement.dto.response.CourtEventResponse;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.CourtEvent;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.entity.MatterTimelineEvent;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventType;
import com.lawfirm.erp.modules.casemanagement.enums.NextEventType;
import com.lawfirm.erp.modules.casemanagement.enums.OutcomeType;
import com.lawfirm.erp.modules.casemanagement.enums.TimelineEventType;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtEventRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterTimelineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The Tarik/Peshi loop. Marking an event HELD forces the "what did the court give next?"
 * answer — which both records the outcome and creates the next CourtEvent row,
 * the direct digital equivalent of writing the next date in the diary.
 */
@Service
@RequiredArgsConstructor
public class CourtEventServiceImpl implements CourtEventService {

    private final CourtEventRepository courtEventRepository;
    private final CourtCaseRepository courtCaseRepository;
    private final MatterRepository matterRepository;
    private final MatterTimelineRepository matterTimelineRepository;
    private final AuditService auditService;
    private final CourtCaseService courtCaseService;

    @Transactional
    public CourtEventResponse scheduleEvent(String ourCourtCaseRef, ScheduleCourtEventRequest request) {
        UUID firmId = getRequiredFirmId();
        CourtCase cc = findCourtCase(ourCourtCaseRef, firmId);

        String conflictWarning = null;
        if (request.getAttendingAdvocateId() != null && request.getScheduledTime() != null) {
            List<CourtEvent> conflicts = findConflicts(firmId, request.getAttendingAdvocateId(),
                    request.getScheduledDate(), request.getScheduledTime(), request.getEndTime(), null);
            if (!conflicts.isEmpty()) {
                if (request.getEventType() == CourtEventType.PESHI) {
                    // Actual hearings must never double-book the advocate.
                    throw new BusinessRuleException(
                            "Advocate has a scheduling conflict at the given time (Peshi events cannot overlap)");
                }
                // Tarik dates are administrative (often handled by a junior/clerk) — allow,
                // but surface the overlap so the user can still see it.
                conflictWarning = "Advocate has " + conflicts.size()
                        + " overlapping event(s) at this time — Tarik events allow overlap";
            }
        }

        CourtEvent event = new CourtEvent();
        event.setFirmId(firmId);
        event.setCourtCaseId(cc.getId());
        event.setEventType(request.getEventType());
        event.setSequenceNo(courtEventRepository.maxSequenceNo(cc.getId()) + 1);
        event.setScheduledDate(request.getScheduledDate());
        event.setScheduledTime(request.getScheduledTime());
        event.setEndTime(request.getEndTime());
        event.setStatus(CourtEventStatus.SCHEDULED);
        event.setAttendingAdvocateId(request.getAttendingAdvocateId());
        event.setJudgeName(request.getJudgeName());
        event.setCourtRoom(request.getCourtRoom());
        event.setNotes(request.getNotes());
        event.setActive(true);
        event = courtEventRepository.save(event);

        recordTimeline(cc, TimelineEventType.COURT_EVENT_SCHEDULED,
                request.getEventType() + " scheduled: " + request.getScheduledDate(),
                "Sequence #" + event.getSequenceNo());
        auditService.log(AuditAction.COURT_EVENT_SCHEDULED, AuditEntity.COURT_EVENT, event.getId(),
                request.getEventType() + " scheduled for " + cc.getOurCourtCaseRef()
                        + " on " + request.getScheduledDate());

        return toResponse(event, cc, conflictWarning);
    }

    public List<CourtEventResponse> listEvents(String ourCourtCaseRef) {
        UUID firmId = getRequiredFirmId();
        CourtCase cc = findCourtCase(ourCourtCaseRef, firmId);
        return courtEventRepository.findByCourtCaseIdAndFirmIdOrderBySequenceNoAsc(cc.getId(), firmId)
                .stream()
                .map(e -> toResponse(e, cc))
                .collect(Collectors.toList());
    }

    public CourtEventResponse getEvent(UUID eventId) {
        UUID firmId = getRequiredFirmId();
        CourtEvent event = findEvent(eventId, firmId);
        CourtCase cc = courtCaseRepository.findById(event.getCourtCaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Court case not found"));
        return toResponse(event, cc);
    }

    @Transactional
    public CourtEventResponse updateEvent(UUID eventId, UpdateCourtEventRequest request) {
        UUID firmId = getRequiredFirmId();
        CourtEvent event = findEvent(eventId, firmId);
        CourtCase cc = courtCaseRepository.findById(event.getCourtCaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Court case not found"));

        if (request.getScheduledDate() != null) event.setScheduledDate(request.getScheduledDate());
        if (request.getScheduledTime() != null) event.setScheduledTime(request.getScheduledTime());
        if (request.getEndTime() != null) event.setEndTime(request.getEndTime());
        if (request.getJudgeName() != null) event.setJudgeName(request.getJudgeName());
        if (request.getCourtRoom() != null) event.setCourtRoom(request.getCourtRoom());
        if (request.getNotes() != null) event.setNotes(request.getNotes());
        if (request.getAttendingAdvocateId() != null) {
            event.setAttendingAdvocateId(request.getAttendingAdvocateId());
        }

        String conflictWarning = null;
        if (event.getAttendingAdvocateId() != null && event.getScheduledTime() != null) {
            List<CourtEvent> conflicts = findConflicts(firmId, event.getAttendingAdvocateId(),
                    event.getScheduledDate(), event.getScheduledTime(), event.getEndTime(), eventId);
            if (!conflicts.isEmpty()) {
                if (event.getEventType() == CourtEventType.PESHI) {
                    throw new BusinessRuleException(
                            "Advocate has a scheduling conflict at the given time (Peshi events cannot overlap)");
                }
                conflictWarning = "Advocate has " + conflicts.size()
                        + " overlapping event(s) at this time — Tarik events allow overlap";
            }
        }

        event = courtEventRepository.save(event);
        auditService.log(AuditAction.COURT_EVENT_UPDATED, AuditEntity.COURT_EVENT, event.getId(),
                "Event updated: " + cc.getOurCourtCaseRef() + " #" + event.getSequenceNo());

        return toResponse(event, cc, conflictWarning);
    }

    @Transactional
    public CourtEventResponse markHeld(UUID eventId, MarkCourtEventHeldRequest request) {
        UUID firmId = getRequiredFirmId();
        CourtEvent event = findEvent(eventId, firmId);
        CourtCase cc = courtCaseRepository.findById(event.getCourtCaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Court case not found"));
        Matter matter = matterRepository.findById(cc.getMatterId())
                .orElseThrow(() -> new ResourceNotFoundException("Matter not found"));

        if (event.getStatus() != CourtEventStatus.SCHEDULED) {
            throw new BusinessRuleException(
                    "Only scheduled events can be marked held (current status: " + event.getStatus() + ")");
        }

        boolean adjourned = request.getOutcomeType() == OutcomeType.ADJOURNED_NO_PROGRESS;
        event.setStatus(adjourned ? CourtEventStatus.ADJOURNED : CourtEventStatus.HELD);
        event.setOutcome(request.getOutcome());
        event.setOutcomeType(request.getOutcomeType());
        event.setNextEventType(request.getNextEventType());
        if (request.getNotes() != null) event.setNotes(request.getNotes());

        // The loop: what did the court give next?
        if (request.getNextEventType() != NextEventType.NONE) {
            if (request.getNextEventDate() == null) {
                throw new BusinessRuleException(
                        "nextEventDate is required when the court gave a next date");
            }
            if (request.getNextEventType() == NextEventType.TARIK
                    || request.getNextEventType() == NextEventType.PESHI) {
                CourtEvent next = new CourtEvent();
                next.setFirmId(firmId);
                next.setCourtCaseId(cc.getId());
                next.setEventType(request.getNextEventType() == NextEventType.TARIK
                        ? CourtEventType.TARIK : CourtEventType.PESHI);
                next.setSequenceNo(courtEventRepository.maxSequenceNo(cc.getId()) + 1);
                next.setScheduledDate(request.getNextEventDate());
                next.setScheduledTime(request.getNextEventTime());
                next.setStatus(CourtEventStatus.SCHEDULED);
                next.setAttendingAdvocateId(event.getAttendingAdvocateId());
                next.setJudgeName(event.getJudgeName());
                next.setCourtRoom(event.getCourtRoom());
                next.setActive(true);
                next = courtEventRepository.save(next);
                event.setNextEventId(next.getId());
            }
            // NextEventType.JUDGMENT → no event row; judgment is recorded via the
            // court-case judgment endpoint once the court delivers it.
        }

        event = courtEventRepository.save(event);

        // Outcome side effects on the CourtCase. The judgment path is delegated to
        // CourtCaseService.recordJudgmentInternal so /held and the judgment endpoint can
        // never get out of sync: a DECIDED case ALWAYS carries the judgment fields and a
        // computed appeal deadline (marking held alone can never decide a case).
        if (request.getOutcomeType() == OutcomeType.JUDGMENT_DELIVERED) {
            if (request.getJudgmentDate() == null
                    || request.getJudgmentSummary() == null || request.getJudgmentSummary().isBlank()) {
                throw new BusinessRuleException(
                        "outcomeType=JUDGMENT_DELIVERED requires judgmentDate and judgmentSummary inline — "
                                + "a court case can only become DECIDED with the judgment on record");
            }
            courtCaseService.recordJudgmentInternal(cc, matter,
                    request.getJudgmentDate(), request.getJudgmentSummary(),
                    request.getDecisionInFavorOfPartyId());
        } else if (request.getOutcomeType() == OutcomeType.WITHDRAWN) {
            cc.setStatus(CourtCaseStatus.WITHDRAWN);
            courtCaseRepository.save(cc);
        }

        recordTimeline(cc, adjourned ? TimelineEventType.COURT_EVENT_ADJOURNED : TimelineEventType.COURT_EVENT_HELD,
                (adjourned ? "Event adjourned" : "Event held") + ": " + cc.getOurCourtCaseRef() + " #" + event.getSequenceNo(),
                request.getOutcome());
        auditService.log(adjourned ? AuditAction.COURT_EVENT_ADJOURNED : AuditAction.COURT_EVENT_HELD,
                AuditEntity.COURT_EVENT, event.getId(),
                (adjourned ? "Event adjourned" : "Event held") + ": " + cc.getOurCourtCaseRef()
                        + " #" + event.getSequenceNo() + " (" + request.getOutcomeType() + ")");

        return toResponse(event, cc);
    }

    @Transactional
    public void cancelEvent(UUID eventId) {
        UUID firmId = getRequiredFirmId();
        CourtEvent event = findEvent(eventId, firmId);
        CourtCase cc = courtCaseRepository.findById(event.getCourtCaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Court case not found"));

        if (event.getStatus() == CourtEventStatus.HELD) {
            throw new BusinessRuleException("A held event cannot be canceled");
        }
        event.setStatus(CourtEventStatus.CANCELED);
        courtEventRepository.save(event);

        recordTimeline(cc, TimelineEventType.COURT_EVENT_CANCELLED,
                "Event cancelled: " + cc.getOurCourtCaseRef() + " #" + event.getSequenceNo(), null);
        auditService.log(AuditAction.COURT_EVENT_CANCELLED, AuditEntity.COURT_EVENT, event.getId(),
                "Event cancelled: " + cc.getOurCourtCaseRef() + " #" + event.getSequenceNo());
    }

    private UUID getRequiredFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }

    private CourtCase findCourtCase(String ourCourtCaseRef, UUID firmId) {
        return courtCaseRepository.findByOurCourtCaseRefAndFirmId(ourCourtCaseRef, firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Court case not found: " + ourCourtCaseRef));
    }

    private CourtEvent findEvent(UUID eventId, UUID firmId) {
        return courtEventRepository.findByIdAndFirmId(eventId, firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Court event not found: " + eventId));
    }

    private List<CourtEvent> findConflicts(UUID firmId, UUID advocateId, LocalDate date,
                                           java.time.LocalTime startTime, java.time.LocalTime endTime,
                                           UUID excludeId) {
        return courtEventRepository.findConflicts(
                firmId, advocateId, date, startTime, endTime, excludeId);
    }

    private void recordTimeline(CourtCase cc, TimelineEventType type, String title, String description) {
        MatterTimelineEvent event = new MatterTimelineEvent();
        event.setFirmId(cc.getFirmId());
        event.setMatterId(cc.getMatterId());
        event.setCourtCaseId(cc.getId());
        event.setEventType(type);
        event.setTitle(title);
        event.setDescription(description);
        matterTimelineRepository.save(event);
    }

    private CourtEventResponse toResponse(CourtEvent event, CourtCase cc) {
        return toResponse(event, cc, null);
    }

    private CourtEventResponse toResponse(CourtEvent event, CourtCase cc, String conflictWarning) {
        Matter matter = cc.getMatterId() != null
                ? matterRepository.findById(cc.getMatterId()).orElse(null) : null;

        return CourtEventResponse.builder()
                .id(event.getId())
                .courtCaseId(cc.getId())
                .ourCourtCaseRef(cc.getOurCourtCaseRef())
                .matterNumber(matter != null ? matter.getMatterNumber() : null)
                .matterTitle(matter != null ? matter.getTitle() : null)
                .eventType(event.getEventType())
                .sequenceNo(event.getSequenceNo())
                .scheduledDate(event.getScheduledDate())
                .scheduledTime(event.getScheduledTime())
                .endTime(event.getEndTime())
                .status(event.getStatus())
                .outcome(event.getOutcome())
                .outcomeType(event.getOutcomeType())
                .nextEventType(event.getNextEventType())
                .nextEventId(event.getNextEventId())
                .attendingAdvocateId(event.getAttendingAdvocateId())
                .judgeName(event.getJudgeName())
                .courtRoom(event.getCourtRoom())
                .notes(event.getNotes())
                .conflictWarning(conflictWarning)
                .createdAt(event.getCreatedAt())
                .build();
    }
}
