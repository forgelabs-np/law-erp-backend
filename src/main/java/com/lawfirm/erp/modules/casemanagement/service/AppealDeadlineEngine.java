package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.casemanagement.entity.AppealDeadlineRule;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStage;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import com.lawfirm.erp.modules.casemanagement.repository.AppealDeadlineRuleRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import com.lawfirm.erp.modules.notification.event.NotificationEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Statutory appeal-window engine.
 *
 * Deadlines are seeded from the procedure codes (National Civil/Criminal Procedure Code 2074):
 *   - Civil appeal from District Court: 30 days (+15 extension)
 *   - Criminal appeal, state plaintiff: 70 days (+30 extension)
 *   - Criminal appeal, private complaint: 30 days (+30 extension)
 *
 * A scheduled watcher notices when "nothing happened" — the appeal window lapsed with
 * no appeal filed and no child CourtCase — and marks the judgment final (appealLapsed),
 * nudging the matter toward DORMANT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppealDeadlineEngine {

    private final AppealDeadlineRuleRepository ruleRepository;
    private final CourtCaseRepository courtCaseRepository;
    private final MatterRepository matterRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @PostConstruct
    public void seedRules() {
        seed(CourtLevel.DISTRICT, MatterType.CIVIL, false, 30, 15, false,
                "Civil appeal from District Court (NCPC 2074 s.??)");
        seed(CourtLevel.DISTRICT, MatterType.CRIMINAL, true, 70, 30, false,
                "Criminal appeal from District Court, state plaintiff");
        seed(CourtLevel.DISTRICT, MatterType.CRIMINAL, false, 30, 30, false,
                "Criminal appeal from District Court, private complaint");
        // High Court → Supreme Court is a leave-petition process, not an automatic
        // appeal: the seeded days still bound the leave window, and the flag tells
        // the UI/docs the process is materially different.
        seed(CourtLevel.HIGH, MatterType.CIVIL, false, 35, 15, true,
                "Further appeal to Supreme Court (civil) — leave petition required");
        seed(CourtLevel.HIGH, MatterType.CRIMINAL, false, 35, 30, true,
                "Further appeal to Supreme Court (criminal) — leave petition required");
        seed(CourtLevel.HIGH, MatterType.CRIMINAL, true, 70, 30, true,
                "Further appeal to Supreme Court (criminal, state plaintiff) — leave petition required");
    }

    private void seed(CourtLevel level, MatterType type, boolean partyIsState,
                      int days, int extensionDays, boolean requiresLeave, String description) {
        if (ruleRepository.findByCourtLevelAppealedFromAndMatterTypeAndPartyIsState(
                level, type, partyIsState).isEmpty()) {
            AppealDeadlineRule rule = new AppealDeadlineRule();
            rule.setCourtLevelAppealedFrom(level);
            rule.setMatterType(type);
            rule.setPartyIsState(partyIsState);
            rule.setDays(days);
            rule.setExtensionDays(extensionDays);
            rule.setRequiresLeave(requiresLeave);
            rule.setDescription(description);
            rule.setActive(true);
            ruleRepository.save(rule);
            log.info("  + Appeal deadline rule: {} {} partyIsState={} -> {}d (+{}d) requiresLeave={}",
                    level, type, partyIsState, days, extensionDays, requiresLeave);
        }
    }

    /**
     * Computes the appeal deadline from a judgment date, or null when no rule applies
     * (e.g. a writ order — no statutory appeal window in the usual sense).
     */
    public LocalDate compute(CourtLevel courtLevelAppealedFrom, MatterType matterType,
                             boolean partyIsState, LocalDate judgmentDate) {
        if (judgmentDate == null) return null;
        return ruleRepository
                .findByCourtLevelAppealedFromAndMatterTypeAndPartyIsState(
                        courtLevelAppealedFrom, matterType, partyIsState)
                .map(r -> judgmentDate.plusDays(r.getDays()))
                .orElse(null);
    }

    /**
     * Daily watcher (03:05): DECIDED cases with an appeal deadline inside the
     * next 3 days (T-3 through T-1) get an in-app APPEAL_DEADLINE alert to
     * the case's advocate. Day-bucketed dedupKey keeps re-runs idempotent.
     */
    @Scheduled(cron = "0 5 3 * * *")
    public void checkUpcomingDeadlines() {
        LocalDate today = LocalDate.now();
        List<CourtCase> upcoming = courtCaseRepository
                .findByStatusAndAppealDeadlineBefore(CourtCaseStatus.DECIDED, today.plusDays(3))
                .stream()
                .filter(cc -> cc.getStage() == CourtCaseStage.JUDGMENT_DELIVERED)
                .filter(cc -> !cc.isAppealLapsed())
                .filter(cc -> cc.getAppealDeadline() != null)
                .filter(cc -> !cc.getAppealDeadline().isBefore(today)) // deadline is today..T+3
                .collect(Collectors.toList());
        if (upcoming.isEmpty()) return;

        log.info("AppealDeadlineEngine: {} case(s) with appeal deadline within 3 days", upcoming.size());
        for (CourtCase cc : upcoming) {
            publishDeadlineAlert(cc, cc.getAppealDeadline());
        }
    }

    private void publishDeadlineAlert(CourtCase cc, LocalDate deadline) {
        Matter matter = matterRepository.findById(cc.getMatterId()).orElse(null);
        if (matter == null) {
            log.warn("AppealDeadlineEngine: matter {} missing for court case {}", cc.getMatterId(), cc.getId());
            return;
        }
        long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), deadline);
        // Day-bucketed by deadline: re-runs within the same window dedup downstream.
        String dedupKey = "APPEAL_DEADLINE:COURT_CASE:" + cc.getId() + ":" + deadline;

        if (cc.getAdvocateId() != null) {
            eventPublisher.publishEvent(new NotificationEvent(
                    cc.getFirmId(), cc.getAdvocateId(), null, false,
                    NotificationType.APPEAL_DEADLINE,
                    "COURT_CASE", cc.getId(), dedupKey,
                    java.util.Map.of(
                            "matterNumber", matter.getMatterNumber(),
                            "courtCaseRef", cc.getOurCourtCaseRef(),
                            "deadline", deadline.toString(),
                            "daysRemaining", String.valueOf(daysRemaining))
            ));
        } else {
            // No per-case advocate recorded — the firm admins own the deadline.
            eventPublisher.publishEvent(new NotificationEvent(
                    cc.getFirmId(), null, "FIRM_ADMIN", false,
                    NotificationType.APPEAL_DEADLINE,
                    "COURT_CASE", cc.getId(), dedupKey,
                    java.util.Map.of(
                            "matterNumber", matter.getMatterNumber(),
                            "courtCaseRef", cc.getOurCourtCaseRef(),
                            "deadline", deadline.toString(),
                            "daysRemaining", String.valueOf(daysRemaining))
            ));
        }
    }

    /**
     * Daily watcher (03:00): DECIDED cases whose appeal window lapsed with no child
     * court case get appealLapsed=true (judgment final); the matter is nudged to DORMANT.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void closeLapsedAppeals() {
        LocalDate today = LocalDate.now();
        List<CourtCase> lapsed = courtCaseRepository
                .findByStatusAndAppealDeadlineBefore(CourtCaseStatus.DECIDED, today)
                .stream()
                .filter(cc -> cc.getStage() == CourtCaseStage.JUDGMENT_DELIVERED)
                .filter(cc -> !courtCaseRepository.existsByParentCourtCaseId(cc.getId()))
                .filter(cc -> !cc.isAppealLapsed())
                .collect(Collectors.toList());

        if (lapsed.isEmpty()) return;
        log.info("AppealDeadlineEngine: marking {} decided case(s) final (no appeal filed)", lapsed.size());

        Set<UUID> matterIds = lapsed.stream().map(CourtCase::getMatterId).collect(Collectors.toSet());
        List<Matter> matters = matterRepository.findAllById(matterIds);

        for (CourtCase cc : lapsed) {
            cc.setAppealLapsed(true);
            courtCaseRepository.save(cc);
            auditService.log(AuditAction.COURT_CASE_CLOSED, AuditEntity.COURT_CASE, cc.getId(),
                    "Appeal window lapsed with no appeal filed: " + cc.getOurCourtCaseRef());

            matterRepository.findById(cc.getMatterId()).ifPresent(m ->
                    eventPublisher.publishEvent(NotificationEvent.toRole(
                            cc.getFirmId(), "FIRM_ADMIN", NotificationType.APPEAL_LAPSED,
                            "COURT_CASE", cc.getId(),
                            java.util.Map.of(
                                    "matterNumber", m.getMatterNumber(),
                                    "courtCaseRef", cc.getOurCourtCaseRef()))));
        }

        for (Matter matter : matters) {
            UUID leafId = matter.getCurrentCourtCaseId();
            boolean leafLapsed = lapsed.stream().anyMatch(cc -> cc.getId().equals(leafId));
            if (!leafLapsed || matter.getStatus() == MatterStatus.CLOSED) continue;

            List<CourtCase> otherOpen = courtCaseRepository.findByMatterIdAndFirmIdOrderByCreatedAtAsc(
                            matter.getId(), matter.getFirmId()).stream()
                    .filter(cc -> cc.getStatus() != CourtCaseStatus.CLOSED)
                    .filter(cc -> cc.getStatus() != CourtCaseStatus.APPEALED)
                    .filter(cc -> cc.getStatus() != CourtCaseStatus.REMANDED)
                    .collect(Collectors.toList());
            boolean onlyLapsedLeafOpen = otherOpen.stream()
                    .allMatch(cc -> cc.getId().equals(leafId) || cc.isAppealLapsed());

            if (onlyLapsedLeafOpen && !otherOpen.isEmpty()) {
                matter.setStatus(MatterStatus.DORMANT);
                matterRepository.save(matter);
                auditService.log(AuditAction.MATTER_UPDATED, AuditEntity.MATTER, matter.getId(),
                        "Matter moved to DORMANT (appeal window lapsed): " + matter.getMatterNumber());
            }
        }
    }
}
