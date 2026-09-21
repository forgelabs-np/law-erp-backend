package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.modules.casemanagement.dto.response.CalendarEventResponse;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.CourtEvent;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.auth.security.ReadScopeGuard;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtEventRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterPartyRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The calendar is a read-only view over the unified CourtEvent table —
 * spanning all CourtCases (and thus all court levels) per advocate/firm.
 */
@Service
@RequiredArgsConstructor
public class CalendarServiceImpl implements CalendarService {

    private final CourtEventRepository courtEventRepository;
    private final CourtCaseRepository courtCaseRepository;
    private final MatterRepository matterRepository;
    private final MatterPartyRepository matterPartyRepository;
    private final ReadScopeGuard readScopeGuard;

    public List<CalendarEventResponse> getCalendar(UUID firmId, LocalDate from, LocalDate to, UUID advocateId) {
        List<CourtEvent> events;
        if (advocateId != null) {
            List<UUID> assignedCourtCaseIds = getAssignedCourtCaseIds(firmId, advocateId);
            if (assignedCourtCaseIds.isEmpty()) {
                return List.of();
            }
            events = courtEventRepository
                    .findByCourtCaseIdInAndFirmIdAndAttendingAdvocateIdAndScheduledDateBetween(
                            assignedCourtCaseIds, firmId, advocateId, from, to);
        } else {
            events = courtEventRepository
                    .findByFirmIdAndScheduledDateBetweenOrderByScheduledDateAscScheduledTimeAsc(
                            firmId, from, to, Pageable.unpaged())
                    .getContent();
        }
        return enrich(visibleToCaller(firmId, events));
    }

    public List<CalendarEventResponse> getTodayEvents(UUID firmId, UUID advocateId) {
        LocalDate today = LocalDate.now();
        List<CourtEvent> events;
        if (advocateId != null) {
            List<UUID> assignedCourtCaseIds = getAssignedCourtCaseIds(firmId, advocateId);
            if (assignedCourtCaseIds.isEmpty()) {
                return List.of();
            }
            events = courtEventRepository
                    .findByCourtCaseIdInAndFirmIdAndAttendingAdvocateIdAndScheduledDate(
                            assignedCourtCaseIds, firmId, advocateId, today);
        } else {
            events = courtEventRepository.findByFirmIdAndScheduledDate(firmId, today);
        }
        return enrich(visibleToCaller(firmId, events));
    }

    public List<CalendarEventResponse> getUpcomingEvents(UUID firmId, int days, UUID advocateId) {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(days);
        List<CourtEvent> events;
        if (advocateId != null) {
            List<UUID> assignedCourtCaseIds = getAssignedCourtCaseIds(firmId, advocateId);
            if (assignedCourtCaseIds.isEmpty()) {
                return List.of();
            }
            events = courtEventRepository
                    .findByCourtCaseIdInAndFirmIdAndAttendingAdvocateIdAndScheduledDateBetween(
                            assignedCourtCaseIds, firmId, advocateId, from, to);
        } else {
            events = courtEventRepository.findByFirmIdAndScheduledDateBetween(firmId, from, to);
        }
        return enrich(visibleToCaller(firmId, events));
    }

    /**
     * A client-portal account may only see hearings belonging to its own matters.
     * Staff are returned the full firm calendar unchanged.
     */
    private List<CourtEvent> visibleToCaller(UUID firmId, List<CourtEvent> events) {
        if (!readScopeGuard.isClientScope() || events.isEmpty()) {
            return events;
        }
        Set<UUID> allowedCaseIds = new java.util.HashSet<>(clientCourtCaseIds(firmId));
        return events.stream()
                .filter(e -> allowedCaseIds.contains(e.getCourtCaseId()))
                .collect(Collectors.toList());
    }

    /** Court cases of every matter owned by the calling client — column link or marked party. */
    private List<UUID> clientCourtCaseIds(UUID firmId) {
        UUID clientId = readScopeGuard.currentUserId();
        Set<UUID> matterIds = new java.util.HashSet<>(
                matterRepository.findByClientUserIdAndFirmIdOrderByCreatedAtDesc(clientId, firmId)
                        .stream().map(Matter::getId).collect(Collectors.toList()));
        matterPartyRepository.findByClientIdAndFirmIdAndOurClientTrue(clientId, firmId)
                .forEach(p -> matterIds.add(p.getMatterId()));
        if (matterIds.isEmpty()) {
            return List.of();
        }
        return courtCaseRepository.findIdsByMatterIdIn(List.copyOf(matterIds));
    }

    /**
     * Returns court case IDs for matters where the employee is the assigned partner.
     * This ensures employees only see calendar events from matters they own.
     */
    private List<UUID> getAssignedCourtCaseIds(UUID firmId, UUID advocateId) {
        List<UUID> matterIds = matterRepository.findIdsByFirmIdAndAssignedPartnerId(firmId, advocateId);
        if (matterIds.isEmpty()) {
            return List.of();
        }
        return courtCaseRepository.findIdsByMatterIdIn(matterIds);
    }

    /**
     * Two batched lookups (court cases, then matters) — no N+1.
     */
    private List<CalendarEventResponse> enrich(List<CourtEvent> events) {
        if (events.isEmpty()) return List.of();

        Set<UUID> courtCaseIds = events.stream().map(CourtEvent::getCourtCaseId).collect(Collectors.toSet());
        Map<UUID, CourtCase> caseMap = courtCaseRepository.findAllById(courtCaseIds).stream()
                .collect(Collectors.toMap(CourtCase::getId, Function.identity()));

        Map<UUID, Matter> matterMap = caseMap.values().isEmpty() ? Map.of()
                : matterRepository.findAllById(
                        caseMap.values().stream().map(CourtCase::getMatterId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Matter::getId, Function.identity()));

        return events.stream().map(e -> {
            CourtCase cc = caseMap.get(e.getCourtCaseId());
            Matter m = cc != null ? matterMap.get(cc.getMatterId()) : null;
            return CalendarEventResponse.builder()
                    .id(e.getId())
                    .courtCaseId(e.getCourtCaseId())
                    .ourCourtCaseRef(cc != null ? cc.getOurCourtCaseRef() : null)
                    .matterNumber(m != null ? m.getMatterNumber() : null)
                    .matterTitle(m != null ? m.getTitle() : null)
                    .eventType(e.getEventType())
                    .scheduledDate(e.getScheduledDate())
                    .scheduledTime(e.getScheduledTime())
                    .endTime(e.getEndTime())
                    .courtRoom(e.getCourtRoom())
                    .status(e.getStatus())
                    .attendingAdvocateId(e.getAttendingAdvocateId())
                    .build();
        }).collect(Collectors.toList());
    }
}
