package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.CourtEvent;
import com.lawfirm.erp.modules.casemanagement.entity.HearingReminderLog;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.entity.MatterParty;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventType;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtEventRepository;
import com.lawfirm.erp.modules.casemanagement.repository.HearingReminderLogRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterPartyRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.modules.email.dto.HearingReminderDetails;
import com.lawfirm.erp.modules.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HearingReminderServiceImpl implements HearingReminderService {

    private final CourtEventRepository courtEventRepository;
    private final CourtCaseRepository courtCaseRepository;
    private final MatterRepository matterRepository;
    private final MatterPartyRepository matterPartyRepository;
    private final UserRepository userRepository;
    private final FirmRepository firmRepository;
    private final HearingReminderLogRepository reminderLogRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public int sendRemindersForDate(LocalDate date) {
        List<CourtEvent> events = courtEventRepository
                .findByEventTypeAndStatusAndScheduledDate(CourtEventType.PESHI, CourtEventStatus.SCHEDULED, date);
        if (events.isEmpty()) {
            log.info("Hearing reminders: no PESHI/SCHEDULED hearings on {}", date);
            return 0;
        }

        // ── Batch-load the whole context for the day (no N+1) ────────────────
        Set<UUID> courtCaseIds = events.stream().map(CourtEvent::getCourtCaseId).collect(Collectors.toSet());
        Map<UUID, CourtCase> caseById = courtCaseRepository.findAllById(courtCaseIds).stream()
                .collect(Collectors.toMap(CourtCase::getId, Function.identity()));

        Set<UUID> matterIds = caseById.values().stream().map(CourtCase::getMatterId).collect(Collectors.toSet());
        Map<UUID, Matter> matterById = matterIds.isEmpty() ? Map.of()
                : matterRepository.findAllById(matterIds).stream()
                        .collect(Collectors.toMap(Matter::getId, Function.identity()));

        Set<UUID> firmIds = events.stream().map(CourtEvent::getFirmId).collect(Collectors.toSet());
        Map<UUID, Firm> firmById = firmIds.isEmpty() ? Map.of()
                : firmRepository.findAllById(firmIds).stream()
                        .collect(Collectors.toMap(Firm::getId, Function.identity()));

        // Our-client parties per matter, queried per firm (parties are firm-scoped)
        Map<UUID, List<MatterParty>> partiesByMatter = new HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> entry : mattersByFirm(matterById, caseById, events).entrySet()) {
            List<MatterParty> parties = matterPartyRepository
                    .findByMatterIdInAndFirmIdAndOurClientTrue(List.copyOf(entry.getValue()), entry.getKey());
            for (MatterParty p : parties) {
                partiesByMatter.computeIfAbsent(p.getMatterId(), k -> new ArrayList<>()).add(p);
            }
        }

        // All recipient users in one query: attending advocates + linked clients
        Set<UUID> userIds = new HashSet<>();
        events.forEach(e -> { if (e.getAttendingAdvocateId() != null) userIds.add(e.getAttendingAdvocateId()); });
        partiesByMatter.values().forEach(ps -> ps.forEach(p -> { if (p.getClientId() != null) userIds.add(p.getClientId()); }));
        Map<UUID, User> userById = userIds.isEmpty() ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        // ── Dispatch per event; one failure never stops the batch ────────────
        int dispatched = 0;
        for (CourtEvent event : events) {
            try {
                CourtCase cc = caseById.get(event.getCourtCaseId());
                if (cc == null) { log.warn("Hearing reminder: court case {} missing for event {}", event.getCourtCaseId(), event.getId()); continue; }
                Matter matter = matterById.get(cc.getMatterId());
                if (matter == null) { log.warn("Hearing reminder: matter {} missing for event {}", cc.getMatterId(), event.getId()); continue; }
                Firm firm = firmById.get(event.getFirmId());

                HearingReminderDetails details = new HearingReminderDetails(
                        firm != null ? firm.getName() : "Your law firm",
                        matter.getMatterNumber(), matter.getTitle(),
                        cc.getOurCourtCaseRef(), cc.getCourtName(),
                        event.getScheduledDate(), event.getScheduledTime(),
                        event.getCourtRoom(), event.getJudgeName());

                // Attending advocate
                if (event.getAttendingAdvocateId() != null) {
                    User advocate = userById.get(event.getAttendingAdvocateId());
                    if (advocate != null && hasEmail(advocate.getEmail())) {
                        UUID logId = claim(event, HearingReminderLog.RecipientType.ADVOCATE, advocate.getEmail(), date);
                        if (logId != null) {
                            emailService.sendHearingReminder(event.getFirmId(), advocate.getId(), advocate.getEmail(),
                                    nameOf(advocate, null), details, HearingReminderLog.RecipientType.ADVOCATE, logId);
                            dispatched++;
                        }
                    } else {
                        log.debug("Hearing reminder: no email for advocate {} (event {})", event.getAttendingAdvocateId(), event.getId());
                    }
                }

                // Linked our-clients (deduped by the claim key — same email only sends once)
                for (MatterParty party : partiesByMatter.getOrDefault(matter.getId(), List.of())) {
                    if (party.getClientId() == null) continue;
                    User client = userById.get(party.getClientId());
                    if (client == null || !hasEmail(client.getEmail())) {
                        log.debug("Hearing reminder: no client user/email for party {} (event {})", party.getId(), event.getId());
                        continue;
                    }
                    UUID logId = claim(event, HearingReminderLog.RecipientType.CLIENT, client.getEmail(), date);
                    if (logId != null) {
                        emailService.sendHearingReminder(event.getFirmId(), client.getId(), client.getEmail(),
                                nameOf(client, party), details, HearingReminderLog.RecipientType.CLIENT, logId);
                        dispatched++;
                    }
                }
            } catch (Exception e) {
                log.error("Hearing reminder failed for event {}: {}", event.getId(), e.getMessage());
            }
        }
        log.info("Hearing reminders for {}: {} email(s) dispatched", date, dispatched);
        return dispatched;
    }

    /** Groups matter ids by their firm — parties are firm-scoped, so we query per firm. */
    private Map<UUID, Set<UUID>> mattersByFirm(Map<UUID, Matter> matterById, Map<UUID, CourtCase> caseById,
                                               List<CourtEvent> events) {
        Map<UUID, Set<UUID>> byFirm = new HashMap<>();
        for (CourtEvent event : events) {
            CourtCase cc = caseById.get(event.getCourtCaseId());
            if (cc == null || !matterById.containsKey(cc.getMatterId())) continue;
            byFirm.computeIfAbsent(event.getFirmId(), k -> new HashSet<>()).add(cc.getMatterId());
        }
        return byFirm;
    }

    /**
     * Claims a reminder slot before dispatch. Returns the log row id, or null
     * when this (event, recipient, date) was already handled — the unique
     * constraint makes the job idempotent across re-runs and restarts.
     */
    private UUID claim(CourtEvent event, HearingReminderLog.RecipientType type, String email, LocalDate date) {
        if (reminderLogRepository.existsByCourtEventIdAndRecipientTypeAndRecipientEmailAndScheduledDate(
                event.getId(), type, email, date)) {
            log.debug("Hearing reminder already sent: event={} type={} to={}", event.getId(), type, email);
            return null;
        }
        HearingReminderLog log = HearingReminderLog.builder()
                .courtEventId(event.getId())
                .recipientType(type)
                .recipientEmail(email)
                .scheduledDate(date)
                .status(HearingReminderLog.Status.SENT)
                .build();
        return reminderLogRepository.save(log).getId();
    }

    private boolean hasEmail(String email) {
        return email != null && !email.isBlank();
    }

    private String nameOf(User user, MatterParty party) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) return user.getFullName();
        if (party != null && party.getFullName() != null && !party.getFullName().isBlank()) return party.getFullName();
        return user.getUsername();
    }
}
