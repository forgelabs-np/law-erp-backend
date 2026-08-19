package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.modules.casemanagement.dto.response.CasePositioningResponse;
import com.lawfirm.erp.modules.casemanagement.dto.response.DashboardResponse;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.CourtEvent;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.enums.*;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtEventRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final MatterRepository matterRepository;
    private final CourtCaseRepository courtCaseRepository;
    private final CourtEventRepository courtEventRepository;
    private final CaseAssignmentService assignmentService;
    private final CurrentUserResolver currentUserResolver;
    private final UserRepository userRepository;

    private static final int STALE_THRESHOLD_DAYS = 90;

    public DashboardResponse getDashboard() {
        UUID firmId = getRequiredFirmId();
        UUID currentUserId = currentUserResolver.getCurrentUserId();
        boolean isAdmin = currentUserResolver.getCurrentUser() != null
                && currentUserResolver.getCurrentUser().getRoles() != null
                && currentUserResolver.getCurrentUser().getRoles().contains("FIRM_ADMIN");

        // For ADVOCATE/PARALEGAL: only show assigned matters
        List<UUID> assignedMatterIds = isAdmin ? null : assignmentService.getAssignedMatterIds(currentUserId, firmId);

        List<Matter> matters = getFilteredMatters(firmId, assignedMatterIds);
        List<Matter> activeMatters = matters.stream()
                .filter(m -> m.getStatus() == MatterStatus.ACTIVE)
                .collect(Collectors.toList());

        // Fetch all leaf court cases for positioning
        List<UUID> leafIds = matters.stream()
                .map(Matter::getCurrentCourtCaseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<UUID, CourtCase> courtCaseMap = leafIds.isEmpty() ? Map.of()
                : courtCaseRepository.findAllById(leafIds).stream()
                        .collect(Collectors.toMap(CourtCase::getId, Function.identity()));

        // Batch: latest event + event counts per court case
        Map<UUID, LocalDate> latestEventDate = leafIds.isEmpty() ? Map.of()
                : courtEventRepository.findLatestPeshiByCourtCaseIds(leafIds).stream()
                        .collect(Collectors.toMap(r -> (UUID) r[0], r -> (LocalDate) r[1]));

        Map<UUID, Integer> eventCounts = leafIds.isEmpty() ? Map.of()
                : courtEventRepository.countByCourtCaseIds(leafIds).stream()
                        .collect(Collectors.toMap(r -> (UUID) r[0], r -> ((Number) r[1]).intValue()));

        // All events for leaf court cases, grouped once (avoids N+1 per matter)
        Map<UUID, List<CourtEvent>> eventsByCourtCase = leafIds.isEmpty() ? Map.of()
                : courtEventRepository.findByCourtCaseIdInAndFirmIdOrderBySequenceNoAsc(leafIds, firmId)
                        .stream()
                        .collect(Collectors.groupingBy(CourtEvent::getCourtCaseId));

        // Today's events
        List<CourtEvent> todayEvents = getTodayEvents(firmId, isAdmin ? null : currentUserId);
        Map<UUID, CourtEvent> todayEventMap = todayEvents.stream()
                .collect(Collectors.toMap(CourtEvent::getId, Function.identity()));

        // Batch resolve court cases + matters for today's events
        Set<UUID> todayCCIds = todayEvents.stream()
                .map(CourtEvent::getCourtCaseId).collect(Collectors.toSet());
        Map<UUID, CourtCase> todayCCMap = todayCCIds.isEmpty() ? Map.of()
                : courtCaseRepository.findAllById(todayCCIds).stream()
                        .collect(Collectors.toMap(CourtCase::getId, Function.identity()));

        Set<UUID> todayMatterIds = todayCCMap.values().stream()
                .map(CourtCase::getMatterId).collect(Collectors.toSet());
        Map<UUID, Matter> todayMatterMap = todayMatterIds.isEmpty() ? Map.of()
                : matterRepository.findAllById(todayMatterIds).stream()
                        .collect(Collectors.toMap(Matter::getId, Function.identity()));

        // Batch resolve advocate names for today's events
        Set<UUID> advocateIds = todayEvents.stream()
                .map(CourtEvent::getAttendingAdvocateId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> advocateNames = advocateIds.isEmpty() ? Map.of()
                : userRepository.findAllById(advocateIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getFullName));

        // Build today's event summaries
        List<DashboardResponse.TodayEventSummary> todaySummaries = todayEvents.stream()
                .map(e -> {
                    CourtCase cc = todayCCMap.get(e.getCourtCaseId());
                    Matter m = cc != null ? todayMatterMap.get(cc.getMatterId()) : null;
                    return DashboardResponse.TodayEventSummary.builder()
                            .eventId(e.getId())
                            .ourCourtCaseRef(cc != null ? cc.getOurCourtCaseRef() : null)
                            .matterNumber(m != null ? m.getMatterNumber() : null)
                            .matterTitle(m != null ? m.getTitle() : null)
                            .eventType(e.getEventType())
                            .scheduledDate(e.getScheduledDate())
                            .scheduledTime(e.getScheduledTime())
                            .endTime(e.getEndTime())
                            .status(e.getStatus())
                            .courtRoom(e.getCourtRoom())
                            .judgeName(e.getJudgeName())
                            .attendingAdvocateId(e.getAttendingAdvocateId())
                            .attendingAdvocateName(e.getAttendingAdvocateId() != null
                                    ? advocateNames.get(e.getAttendingAdvocateId()) : null)
                            .build();
                })
                .collect(Collectors.toList());

        // Build case positioning
        Map<UUID, String> advocateFullNameMap = buildAdvocateMap(matters, courtCaseMap);

        List<CasePositioningResponse> positioning = matters.stream()
                .map(m -> buildPositioning(m, courtCaseMap, latestEventDate, eventCounts,
                        eventsByCourtCase, advocateFullNameMap))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Stale: 90+ days since last hearing
        LocalDate staleCutoff = LocalDate.now().minusDays(STALE_THRESHOLD_DAYS);
        List<CasePositioningResponse> stale = positioning.stream()
                .filter(p -> {
                    if (p.getLastHearingDate() == null) return true;
                    return p.getLastHearingDate().isBefore(staleCutoff);
                })
                .collect(Collectors.toList());

        long dormantCount = matters.stream().filter(m -> m.getStatus() == MatterStatus.DORMANT).count();
        long closedCount = matters.stream().filter(m -> m.getStatus() == MatterStatus.CLOSED).count();

        return DashboardResponse.builder()
                .stats(DashboardResponse.DashboardStats.builder()
                        .totalMatters(matters.size())
                        .activeMatters(activeMatters.size())
                        .dormantMatters(dormantCount)
                        .closedMatters(closedCount)
                        .todayEventsCount(todaySummaries.size())
                        .staleCount(stale.size())
                        .build())
                .todayEvents(todaySummaries)
                .casePositioning(positioning)
                .staleCases(stale)
                .build();
    }

    private List<Matter> getFilteredMatters(UUID firmId, List<UUID> assignedMatterIds) {
        if (assignedMatterIds != null && assignedMatterIds.isEmpty()) {
            return List.of(); // ADVOCATE/PARALEGAL with no assignments
        }
        if (assignedMatterIds != null) {
            return matterRepository.findAllById(assignedMatterIds).stream()
                    .filter(m -> m.getFirmId().equals(firmId))
                    .collect(Collectors.toList());
        }
        // FIRM_ADMIN: all matters
        return matterRepository.findByFirmId(firmId, org.springframework.data.domain.Pageable.unpaged())
                .getContent();
    }

    private List<CourtEvent> getTodayEvents(UUID firmId, UUID advocateId) {
        LocalDate today = LocalDate.now();
        return advocateId != null
                ? courtEventRepository.findByFirmIdAndAttendingAdvocateIdAndScheduledDate(firmId, advocateId, today)
                : courtEventRepository.findByFirmIdAndScheduledDate(firmId, today);
    }

    private CasePositioningResponse buildPositioning(
            Matter matter, Map<UUID, CourtCase> courtCaseMap,
            Map<UUID, LocalDate> latestEventDate, Map<UUID, Integer> eventCounts,
            Map<UUID, List<CourtEvent>> eventsByCourtCase,
            Map<UUID, String> advocateNames) {

        UUID leafId = matter.getCurrentCourtCaseId();
        if (leafId == null) return null;

        CourtCase cc = courtCaseMap.get(leafId);
        if (cc == null) return null;

        LocalDate lastDate = latestEventDate.get(leafId);
        int daysSince = lastDate != null
                ? (int) ChronoUnit.DAYS.between(lastDate, LocalDate.now()) : -1;

        // Latest event details (last hearing)
        List<CourtEvent> events = eventsByCourtCase.getOrDefault(leafId, List.of());
        CourtEvent lastHolding = events.isEmpty() ? null : events.get(events.size() - 1);

        // Next scheduled event
        CourtEvent nextEvent = events.stream()
                .filter(e -> e.getStatus() == CourtEventStatus.SCHEDULED)
                .findFirst().orElse(null);

        return CasePositioningResponse.builder()
                .matterId(matter.getId())
                .matterNumber(matter.getMatterNumber())
                .matterTitle(matter.getTitle())
                .matterType(matter.getMatterType())
                .matterStatus(matter.getStatus())
                .courtCaseId(leafId)
                .ourCourtCaseRef(cc.getOurCourtCaseRef())
                .courtName(cc.getCourtName())
                .stage(cc.getStage())
                .caseStatus(cc.getStatus())
                .advocateId(cc.getAdvocateId())
                .lastHearingDate(lastDate)
                .lastHearingType(lastHolding != null ? lastHolding.getEventType() : null)
                .lastHearingStatus(lastHolding != null ? lastHolding.getStatus() : null)
                .lastHearingOutcome(lastHolding != null ? lastHolding.getOutcome() : null)
                .nextEventDate(nextEvent != null ? nextEvent.getScheduledDate() : null)
                .nextEventType(nextEvent != null ? nextEvent.getEventType() : null)
                .nextEventCourtRoom(nextEvent != null ? nextEvent.getCourtRoom() : null)
                .nextEventJudge(nextEvent != null ? nextEvent.getJudgeName() : null)
                .totalEvents(eventCounts.getOrDefault(leafId, 0))
                .daysSinceLastHearing(daysSince >= 0 ? daysSince : null)
                .stale(lastDate == null || daysSince > STALE_THRESHOLD_DAYS)
                .build();
    }

    private Map<UUID, String> buildAdvocateMap(List<Matter> matters, Map<UUID, CourtCase> courtCaseMap) {
        Set<UUID> advocateIds = new HashSet<>();
        for (Matter m : matters) {
            UUID ccId = m.getCurrentCourtCaseId();
            CourtCase cc = ccId != null ? courtCaseMap.get(ccId) : null;
            if (cc != null && cc.getAdvocateId() != null) {
                advocateIds.add(cc.getAdvocateId());
            }
            if (m.getAssignedPartnerId() != null) {
                advocateIds.add(m.getAssignedPartnerId());
            }
        }
        if (advocateIds.isEmpty()) return Map.of();
        return userRepository.findAllById(advocateIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private UUID getRequiredFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }
}
