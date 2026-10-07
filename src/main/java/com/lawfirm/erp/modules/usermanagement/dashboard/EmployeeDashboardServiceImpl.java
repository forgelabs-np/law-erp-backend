package com.lawfirm.erp.modules.usermanagement.dashboard;

import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.CourtEvent;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtEventRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.modules.casemanagement.service.CaseAssignmentService;
import com.lawfirm.erp.modules.usermanagement.dto.response.EmployeeDashboardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeDashboardServiceImpl implements EmployeeDashboardService {

    private final MatterRepository matterRepository;
    private final CourtCaseRepository courtCaseRepository;
    private final CourtEventRepository courtEventRepository;
    private final CaseAssignmentService assignmentService;

    @Override
    public EmployeeDashboardResponse getDashboard(DashboardScope scope) {
        UUID firmId = getRequiredFirmId(scope);
        UUID userId = scope.getUserId();

        List<UUID> assignedMatterIds = assignmentService.getAssignedMatterIds(userId, firmId);
        if (assignedMatterIds.isEmpty()) {
            return emptyDashboard();
        }

        List<Matter> matters = matterRepository.findAllById(assignedMatterIds).stream()
                .filter(m -> m.getFirmId().equals(firmId))
                .collect(Collectors.toList());

        long activeCount = matters.stream()
                .filter(m -> m.getStatus() == MatterStatus.ACTIVE)
                .count();

        List<UUID> leafIds = matters.stream().map(Matter::getCurrentCourtCaseId)
                .filter(Objects::nonNull).collect(Collectors.toList());
        Map<UUID, Matter> matterMap = matters.stream().collect(Collectors.toMap(Matter::getId, m -> m));
        Map<UUID, CourtCase> ccMap = leafIds.isEmpty() ? Map.of()
                : courtCaseRepository.findAllById(leafIds).stream()
                .collect(Collectors.toMap(CourtCase::getId, c -> c));

        Map<UUID, List<CourtEvent>> eventsByCC = leafIds.isEmpty() ? Map.of()
                : courtEventRepository.findByCourtCaseIdInAndFirmIdOrderBySequenceNoAsc(leafIds, firmId)
                .stream().collect(Collectors.groupingBy(CourtEvent::getCourtCaseId));

        LocalDate today = LocalDate.now();

        List<EmployeeDashboardResponse.MyTodayEvent> todayEvents = buildTodayEvents(
                eventsByCC, ccMap, matterMap, userId, today);

        List<EmployeeDashboardResponse.MyUpcomingHearing> upcomingHearings = buildUpcomingHearings(
                eventsByCC, ccMap, matterMap, today, today.plusDays(14));

        List<EmployeeDashboardResponse.MyUpcomingDeadline> upcomingDeadlines = buildUpcomingDeadlines(
                leafIds, ccMap, matterMap, today, today.plusDays(30));

        List<EmployeeDashboardResponse.MyStaleMatter> staleMatters = buildStaleMatters(
                eventsByCC, ccMap, matterMap, today);

        return EmployeeDashboardResponse.builder()
                .myCaseStats(EmployeeDashboardResponse.MyCaseStats.builder()
                        .openMatters(activeCount)
                        .upcomingHearings(upcomingHearings.size())
                        .staleMatters(staleMatters.size())
                        .build())
                .myTodayEvents(todayEvents)
                .myUpcomingHearings(upcomingHearings)
                .myUpcomingDeadlines(upcomingDeadlines)
                .myStaleMatters(staleMatters)
                .recentUpdates(List.of())
                .build();
    }

    private List<EmployeeDashboardResponse.MyTodayEvent> buildTodayEvents(
            Map<UUID, List<CourtEvent>> eventsByCC, Map<UUID, CourtCase> ccMap,
            Map<UUID, Matter> matterMap, UUID userId, LocalDate today) {

        return eventsByCC.values().stream()
                .flatMap(Collection::stream)
                .filter(e -> today.equals(e.getScheduledDate()))
                .filter(e -> userId.equals(e.getAttendingAdvocateId()))
                .map(e -> {
                    CourtCase cc = ccMap.get(e.getCourtCaseId());
                    Matter m = cc != null ? matterMap.get(cc.getMatterId()) : null;
                    return EmployeeDashboardResponse.MyTodayEvent.builder()
                            .matterTitle(m != null ? m.getTitle() : null)
                            .ourCourtCaseRef(cc != null ? cc.getOurCourtCaseRef() : null)
                            .courtRoom(e.getCourtRoom())
                            .scheduledTime(e.getScheduledTime() != null ? e.getScheduledTime().toString() : null)
                            .judgeName(e.getJudgeName())
                            .status(e.getStatus() != null ? e.getStatus().name() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<EmployeeDashboardResponse.MyUpcomingHearing> buildUpcomingHearings(
            Map<UUID, List<CourtEvent>> eventsByCC, Map<UUID, CourtCase> ccMap,
            Map<UUID, Matter> matterMap, LocalDate from, LocalDate to) {

        List<EmployeeDashboardResponse.MyUpcomingHearing> hearings = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (var entry : eventsByCC.entrySet()) {
            CourtCase cc = ccMap.get(entry.getKey());
            Matter m = cc != null ? matterMap.get(cc.getMatterId()) : null;

            for (CourtEvent e : entry.getValue()) {
                if (e.getStatus() == CourtEventStatus.SCHEDULED
                        && e.getScheduledDate() != null
                        && !e.getScheduledDate().isBefore(from)
                        && !e.getScheduledDate().isAfter(to)) {
                    int daysUntil = (int) ChronoUnit.DAYS.between(today, e.getScheduledDate());
                    hearings.add(EmployeeDashboardResponse.MyUpcomingHearing.builder()
                            .matterTitle(m != null ? m.getTitle() : null)
                            .ourCourtCaseRef(cc != null ? cc.getOurCourtCaseRef() : null)
                            .hearingDate(e.getScheduledDate())
                            .courtName(cc != null ? cc.getCourtName() : null)
                            .daysUntil(daysUntil)
                            .build());
                }
            }
        }

        return hearings.stream()
                .sorted(Comparator.comparing(EmployeeDashboardResponse.MyUpcomingHearing::getHearingDate))
                .collect(Collectors.toList());
    }

    private List<EmployeeDashboardResponse.MyUpcomingDeadline> buildUpcomingDeadlines(
            List<UUID> leafIds, Map<UUID, CourtCase> ccMap,
            Map<UUID, Matter> matterMap, LocalDate from, LocalDate to) {

        List<EmployeeDashboardResponse.MyUpcomingDeadline> deadlines = new ArrayList<>();

        for (UUID leafId : leafIds) {
            CourtCase cc = ccMap.get(leafId);
            if (cc == null) continue;
            Matter m = matterMap.get(cc.getMatterId());

            if (cc.getWrittenStatementDeadline() != null
                    && !cc.getWrittenStatementDeadline().isBefore(from)
                    && !cc.getWrittenStatementDeadline().isAfter(to)) {
                int daysRemaining = (int) ChronoUnit.DAYS.between(from, cc.getWrittenStatementDeadline());
                deadlines.add(EmployeeDashboardResponse.MyUpcomingDeadline.builder()
                        .matterTitle(m != null ? m.getTitle() : null)
                        .ourCourtCaseRef(cc.getOurCourtCaseRef())
                        .deadline(cc.getWrittenStatementDeadline())
                        .deadlineType("WRITTEN_STATEMENT")
                        .daysRemaining(daysRemaining)
                        .isUrgent(daysRemaining <= 3)
                        .build());
            }

            if (cc.getAppealDeadline() != null
                    && !cc.getAppealDeadline().isBefore(from)
                    && !cc.getAppealDeadline().isAfter(to)
                    && !cc.isAppealLapsed()) {
                int daysRemaining = (int) ChronoUnit.DAYS.between(from, cc.getAppealDeadline());
                deadlines.add(EmployeeDashboardResponse.MyUpcomingDeadline.builder()
                        .matterTitle(m != null ? m.getTitle() : null)
                        .ourCourtCaseRef(cc.getOurCourtCaseRef())
                        .deadline(cc.getAppealDeadline())
                        .deadlineType("APPEAL")
                        .daysRemaining(daysRemaining)
                        .isUrgent(daysRemaining <= 7)
                        .build());
            }
        }

        return deadlines.stream()
                .sorted(Comparator.comparing(EmployeeDashboardResponse.MyUpcomingDeadline::getDeadline))
                .collect(Collectors.toList());
    }

    private List<EmployeeDashboardResponse.MyStaleMatter> buildStaleMatters(
            Map<UUID, List<CourtEvent>> eventsByCC, Map<UUID, CourtCase> ccMap,
            Map<UUID, Matter> matterMap, LocalDate today) {

        LocalDate cutoff = today.minusDays(90);
        List<EmployeeDashboardResponse.MyStaleMatter> stale = new ArrayList<>();

        for (var entry : eventsByCC.entrySet()) {
            CourtCase cc = ccMap.get(entry.getKey());
            if (cc == null) continue;
            Matter m = matterMap.get(cc.getMatterId());

            Optional<LocalDate> lastHearing = entry.getValue().stream()
                    .map(CourtEvent::getScheduledDate)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder());

            if (lastHearing.isEmpty() || lastHearing.get().isBefore(cutoff)) {
                int daysSince = lastHearing.isPresent()
                        ? (int) ChronoUnit.DAYS.between(lastHearing.get(), today) : 999;
                stale.add(EmployeeDashboardResponse.MyStaleMatter.builder()
                        .matterTitle(m != null ? m.getTitle() : null)
                        .ourCourtCaseRef(cc.getOurCourtCaseRef())
                        .lastHearingDate(lastHearing.orElse(null))
                        .daysSinceLastHearing(daysSince)
                        .build());
            }
        }

        return stale.stream()
                .sorted(Comparator.comparingInt(EmployeeDashboardResponse.MyStaleMatter::getDaysSinceLastHearing).reversed())
                .collect(Collectors.toList());
    }

    private EmployeeDashboardResponse emptyDashboard() {
        return EmployeeDashboardResponse.builder()
                .myCaseStats(EmployeeDashboardResponse.MyCaseStats.builder()
                        .openMatters(0).upcomingHearings(0).staleMatters(0).build())
                .myTodayEvents(List.of())
                .myUpcomingHearings(List.of())
                .myUpcomingDeadlines(List.of())
                .myStaleMatters(List.of())
                .recentUpdates(List.of())
                .build();
    }

    private UUID getRequiredFirmId(DashboardScope scope) {
        if (scope.getFirmId() == null) throw new ForbiddenException("Firm context required");
        return scope.getFirmId();
    }
}
