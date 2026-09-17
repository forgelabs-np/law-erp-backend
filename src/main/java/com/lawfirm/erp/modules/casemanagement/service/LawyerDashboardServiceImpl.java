package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.modules.casemanagement.dto.response.LawyerDashboardResponse;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStage;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStatus;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.modules.scraper.entity.ClientCase;
import com.lawfirm.erp.modules.scraper.entity.HearingMatch;
import com.lawfirm.erp.modules.scraper.service.HearingMatchingService;
import com.lawfirm.erp.modules.scraper.service.ScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LawyerDashboardServiceImpl implements LawyerDashboardService {

    private final CourtCaseRepository courtCaseRepository;
    private final MatterRepository matterRepository;
    private final ScraperService scraperService;
    private final HearingMatchingService hearingMatchingService;

    @Override
    @Transactional(readOnly = true)
    public LawyerDashboardResponse getLawyerDashboard(UUID advocateId) {
        UUID firmId = getRequiredFirmId();
        List<CourtCase> advocateCases = courtCaseRepository.findByFirmIdAndAdvocateId(firmId, advocateId);
        List<CourtCase> activeCases = advocateCases.stream()
                .filter(cc -> cc.getStatus() == CourtCaseStatus.ACTIVE || cc.getStatus() == CourtCaseStatus.JUDGMENT_AWAITED)
                .collect(Collectors.toList());
        
        LawyerDashboardResponse.DashboardStats stats = buildStats(activeCases);
        List<LawyerDashboardResponse.CriticalCase> criticalCases = buildCriticalCases(activeCases);
        List<LawyerDashboardResponse.UpcomingHearing> hearings = buildUpcomingHearings(advocateCases, 7);
        List<LawyerDashboardResponse.UpcomingDeadline> deadlines = buildUpcomingDeadlines(activeCases, 30);
        List<LawyerDashboardResponse.CaseUpdate> recentUpdates = new ArrayList<>();
        
        return LawyerDashboardResponse.builder()
                .stats(stats).criticalCases(criticalCases).upcomingHearings(hearings)
                .upcomingDeadlines(deadlines).recentUpdates(recentUpdates).build();
    }

    @Override
    @Transactional(readOnly = true)
    public LawyerDashboardResponse.CaseSummary getLawyerCases(UUID advocateId) {
        UUID firmId = getRequiredFirmId();
        List<CourtCase> advocateCases = courtCaseRepository.findByFirmIdAndAdvocateId(firmId, advocateId);
        List<CourtCase> activeCases = advocateCases.stream()
                .filter(cc -> cc.getStatus() == CourtCaseStatus.ACTIVE || cc.getStatus() == CourtCaseStatus.JUDGMENT_AWAITED)
                .collect(Collectors.toList());
        
        List<LawyerDashboardResponse.CriticalCase> cases = buildCriticalCases(activeCases);
        LawyerDashboardResponse.DashboardStats stats = buildStats(activeCases);
        
        return LawyerDashboardResponse.CaseSummary.builder()
                .advocateId(advocateId).advocateName("Advocate").cases(cases).stats(stats).build();
    }

    @Override
    @Transactional(readOnly = true)
    public LawyerDashboardResponse.HearingSummary getLawyerHearings(UUID advocateId, int withinDays) {
        UUID firmId = getRequiredFirmId();
        List<CourtCase> advocateCases = courtCaseRepository.findByFirmIdAndAdvocateId(firmId, advocateId);
        List<LawyerDashboardResponse.UpcomingHearing> hearings = buildUpcomingHearings(advocateCases, withinDays);
        return LawyerDashboardResponse.HearingSummary.builder()
                .advocateId(advocateId).advocateName("Advocate")
                .hearings(hearings).totalHearings(hearings.size()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public LawyerDashboardResponse.DeadlineSummary getLawyerDeadlines(UUID advocateId, int withinDays) {
        UUID firmId = getRequiredFirmId();
        List<CourtCase> advocateCases = courtCaseRepository.findByFirmIdAndAdvocateId(firmId, advocateId);
        List<CourtCase> activeCases = advocateCases.stream()
                .filter(cc -> cc.getStatus() == CourtCaseStatus.ACTIVE || cc.getStatus() == CourtCaseStatus.JUDGMENT_AWAITED)
                .collect(Collectors.toList());
        
        List<LawyerDashboardResponse.UpcomingDeadline> deadlines = buildUpcomingDeadlines(activeCases, withinDays);
        int urgentDeadlines = (int) deadlines.stream().filter(d -> d.isUrgent()).count();
        
        return LawyerDashboardResponse.DeadlineSummary.builder()
                .advocateId(advocateId).advocateName("Advocate")
                .deadlines(deadlines).totalDeadlines(deadlines.size()).urgentDeadlines(urgentDeadlines).build();
    }

    private LawyerDashboardResponse.DashboardStats buildStats(List<CourtCase> activeCases) {
        long totalActiveCases = activeCases.size();
        long casesWithUpcomingHearings = activeCases.stream().filter(this::hasUpcomingHearing).count();
        long casesWithPendingDeadlines = activeCases.stream().filter(this::hasPendingDeadline).count();
        long casesAwaitingJudgment = activeCases.stream()
                .filter(cc -> cc.getStage() == CourtCaseStage.JUDGMENT_AWAITED).count();
        
        return LawyerDashboardResponse.DashboardStats.builder()
                .totalActiveCases(totalActiveCases).casesWithUpcomingHearings(casesWithUpcomingHearings)
                .casesWithPendingDeadlines(casesWithPendingDeadlines).casesAwaitingJudgment(casesAwaitingJudgment)
                .casesNeedingAttention(casesWithUpcomingHearings + casesWithPendingDeadlines + casesAwaitingJudgment)
                .build();
    }

    private List<LawyerDashboardResponse.CriticalCase> buildCriticalCases(List<CourtCase> activeCases) {
        return activeCases.stream().map(cc -> {
            Matter matter = matterRepository.findById(cc.getMatterId()).orElse(null);
            LocalDate nextHearing = getNextHearingDate(cc);
            LocalDate deadline = getDeadline(cc);
            String urgencyReason = determineUrgencyReason(cc, nextHearing, deadline);
            
            return LawyerDashboardResponse.CriticalCase.builder()
                    .courtCaseId(cc.getId()).ourCourtCaseRef(cc.getOurCourtCaseRef())
                    .matterTitle(matter != null ? matter.getTitle() : null)
                    .courtName(cc.getCourtName()).courtLevel(cc.getCourtLevel())
                    .currentStage(cc.getStage()).status(cc.getStatus().name())
                    .nextHearingDate(nextHearing).deadline(deadline).urgencyReason(urgencyReason)
                    .build();
        }).sorted(Comparator.comparing(LawyerDashboardResponse.CriticalCase::getUrgencyReason).reversed())
          .collect(Collectors.toList());
    }

    private List<LawyerDashboardResponse.UpcomingHearing> buildUpcomingHearings(List<CourtCase> advocateCases, int withinDays) {
        List<LawyerDashboardResponse.UpcomingHearing> hearings = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(withinDays);
        
        // Batch-fetch matters to avoid N+1
        Set<UUID> matterIds = advocateCases.stream().map(CourtCase::getMatterId).collect(Collectors.toSet());
        Map<UUID, Matter> matters = matterRepository.findAllById(matterIds).stream()
                .collect(Collectors.toMap(Matter::getId, m -> m));
        
        for (CourtCase cc : advocateCases) {
            Optional<ClientCase> clientCase = scraperService.findClientCaseByCaseNo(cc.getOurCourtCaseRef());
            if (clientCase.isPresent()) {
                List<HearingMatch> matches = hearingMatchingService.findByClientCaseId(clientCase.get().getId());
                for (HearingMatch match : matches) {
                    if (match.getHearingDateAd() != null && !match.getHearingDateAd().isBefore(today) && !match.getHearingDateAd().isAfter(cutoff)) {
                        Matter matter = matters.get(cc.getMatterId());
                        hearings.add(LawyerDashboardResponse.UpcomingHearing.builder()
                                .courtCaseId(cc.getId()).ourCourtCaseRef(cc.getOurCourtCaseRef())
                                .matterTitle(matter != null ? matter.getTitle() : null)
                                .courtName(cc.getCourtName()).hearingDate(match.getHearingDateAd())
                                .hearingDateBs(match.getHearingDateBs()).judgeName(match.getJudgeName())
                                .subject(match.getSubject()).orderType(match.getOrderType())
                                .source(match.getSource()).sourceDescription("Court registry scrape")
                                .isToday(match.getHearingDateAd().isEqual(today))
                                .isTomorrow(match.getHearingDateAd().isEqual(today.plusDays(1)))
                                .build());
                    }
                }
            }
        }
        return hearings.stream().sorted(Comparator.comparing(LawyerDashboardResponse.UpcomingHearing::getHearingDate)).collect(Collectors.toList());
    }

    private List<LawyerDashboardResponse.UpcomingDeadline> buildUpcomingDeadlines(List<CourtCase> activeCases, int withinDays) {
        List<LawyerDashboardResponse.UpcomingDeadline> deadlines = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(withinDays);
        
        for (CourtCase cc : activeCases) {
            if (cc.getWrittenStatementDeadline() != null && !cc.getWrittenStatementDeadline().isBefore(today) && !cc.getWrittenStatementDeadline().isAfter(cutoff)) {
                Matter matter = matterRepository.findById(cc.getMatterId()).orElse(null);
                long daysRemaining = ChronoUnit.DAYS.between(today, cc.getWrittenStatementDeadline());
                deadlines.add(LawyerDashboardResponse.UpcomingDeadline.builder()
                        .courtCaseId(cc.getId()).ourCourtCaseRef(cc.getOurCourtCaseRef())
                        .matterTitle(matter != null ? matter.getTitle() : null).courtName(cc.getCourtName())
                        .deadline(cc.getWrittenStatementDeadline()).deadlineType("WRITTEN_STATEMENT")
                        .description("Written statement filing deadline").daysRemaining((int) daysRemaining)
                        .isUrgent(daysRemaining <= 3).build());
            }
            
            if (cc.getAppealDeadline() != null && !cc.getAppealDeadline().isBefore(today) && !cc.getAppealDeadline().isAfter(cutoff) && !cc.isAppealLapsed()) {
                Matter matter = matterRepository.findById(cc.getMatterId()).orElse(null);
                long daysRemaining = ChronoUnit.DAYS.between(today, cc.getAppealDeadline());
                deadlines.add(LawyerDashboardResponse.UpcomingDeadline.builder()
                        .courtCaseId(cc.getId()).ourCourtCaseRef(cc.getOurCourtCaseRef())
                        .matterTitle(matter != null ? matter.getTitle() : null).courtName(cc.getCourtName())
                        .deadline(cc.getAppealDeadline()).deadlineType("APPEAL")
                        .description("Appeal filing deadline" + (Boolean.TRUE.equals(cc.getAppealRequiresLeave()) ? " (leave to appeal required)" : ""))
                        .daysRemaining((int) daysRemaining).isUrgent(daysRemaining <= 7).build());
            }
        }
        return deadlines.stream().sorted(Comparator.comparing(LawyerDashboardResponse.UpcomingDeadline::getDeadline)).collect(Collectors.toList());
    }

    private boolean hasUpcomingHearing(CourtCase cc) {
        Optional<ClientCase> clientCase = scraperService.findClientCaseByCaseNo(cc.getOurCourtCaseRef());
        if (clientCase.isPresent()) {
            List<HearingMatch> matches = hearingMatchingService.findByClientCaseId(clientCase.get().getId());
            LocalDate today = LocalDate.now();
            LocalDate nextWeek = today.plusDays(7);
            return matches.stream().anyMatch(m -> m.getHearingDateAd() != null && !m.getHearingDateAd().isBefore(today) && !m.getHearingDateAd().isAfter(nextWeek));
        }
        return false;
    }

    private boolean hasPendingDeadline(CourtCase cc) {
        LocalDate today = LocalDate.now();
        LocalDate nextMonth = today.plusDays(30);
        return (cc.getWrittenStatementDeadline() != null && !cc.getWrittenStatementDeadline().isBefore(today) && !cc.getWrittenStatementDeadline().isAfter(nextMonth)) ||
               (cc.getAppealDeadline() != null && !cc.getAppealDeadline().isBefore(today) && !cc.getAppealDeadline().isAfter(nextMonth) && !cc.isAppealLapsed());
    }

    private LocalDate getNextHearingDate(CourtCase cc) {
        Optional<ClientCase> clientCase = scraperService.findClientCaseByCaseNo(cc.getOurCourtCaseRef());
        if (clientCase.isPresent()) {
            List<HearingMatch> matches = hearingMatchingService.findByClientCaseId(clientCase.get().getId());
            LocalDate today = LocalDate.now();
            return matches.stream()
                    .filter(m -> m.getHearingDateAd() != null && !m.getHearingDateAd().isBefore(today))
                    .min(Comparator.comparing(HearingMatch::getHearingDateAd))
                    .map(HearingMatch::getHearingDateAd).orElse(null);
        }
        return null;
    }

    private LocalDate getDeadline(CourtCase cc) {
        LocalDate today = LocalDate.now();
        if (cc.getWrittenStatementDeadline() != null && !cc.getWrittenStatementDeadline().isBefore(today)) {
            return cc.getWrittenStatementDeadline();
        }
        if (cc.getAppealDeadline() != null && !cc.getAppealDeadline().isBefore(today) && !cc.isAppealLapsed()) {
            return cc.getAppealDeadline();
        }
        return null;
    }

    private String determineUrgencyReason(CourtCase cc, LocalDate nextHearing, LocalDate deadline) {
        LocalDate today = LocalDate.now();
        if (nextHearing != null) {
            long daysUntilHearing = ChronoUnit.DAYS.between(today, nextHearing);
            if (daysUntilHearing <= 1) return "CRITICAL - Hearing " + (daysUntilHearing == 0 ? "TODAY" : "TOMORROW");
            if (daysUntilHearing <= 3) return "HIGH - Hearing in " + daysUntilHearing + " days";
        }
        if (deadline != null) {
            long daysUntilDeadline = ChronoUnit.DAYS.between(today, deadline);
            if (daysUntilDeadline <= 3) return "CRITICAL - Deadline in " + daysUntilDeadline + " days";
            if (daysUntilDeadline <= 7) return "HIGH - Deadline in " + daysUntilDeadline + " days";
        }
        if (cc.getStage() == CourtCaseStage.JUDGMENT_AWAITED) return "MEDIUM - Awaiting judgment";
        return "LOW";
    }

    private UUID getRequiredFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }
}
