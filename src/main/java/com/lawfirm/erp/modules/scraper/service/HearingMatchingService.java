package com.lawfirm.erp.modules.scraper.service;

import com.lawfirm.erp.modules.scraper.entity.ClientCase;
import com.lawfirm.erp.modules.scraper.entity.DailyHearing;
import com.lawfirm.erp.modules.scraper.entity.HearingMatch;
import com.lawfirm.erp.modules.scraper.entity.WeeklyHearing;
import com.lawfirm.erp.modules.scraper.enums.ClientCaseStatus;
import com.lawfirm.erp.modules.scraper.enums.HearingSource;
import com.lawfirm.erp.modules.scraper.repository.ClientCaseRepository;
import com.lawfirm.erp.modules.scraper.repository.DailyHearingRepository;
import com.lawfirm.erp.modules.scraper.repository.HearingMatchRepository;
import com.lawfirm.erp.modules.scraper.repository.WeeklyHearingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Joins client cases against ingested hearings on (courtId, caseNoInternal) and pushes new
// matches to the notification channel. Independent of the scrape — it reads whatever is
// already ingested, so a slow/failed scrape never blocks matching. Matches are keyed on
// (clientCaseId, courtId, hearingDateBs); re-runs skip existing rows.
@Slf4j
@Service
@RequiredArgsConstructor
public class HearingMatchingService {

    private final ClientCaseRepository clientCaseRepository;
    private final DailyHearingRepository dailyRepository;
    private final WeeklyHearingRepository weeklyRepository;
    private final HearingMatchRepository matchRepository;
    private final NotificationDispatcher dispatcher;

    @Transactional
    public int matchAndNotify() {
        LocalDate today = LocalDate.now();
        List<ClientCase> active = clientCaseRepository.findAll().stream()
                .filter(c -> c.isActive())
                .filter(c -> c.getCaseStatus() == ClientCaseStatus.ACTIVE)
                .toList();

        int created = 0;
        for (ClientCase cc : active) {
            for (DailyHearing h : dailyRepository
                    .findByCourtIdAndCaseNoInternalAndHearingDateAdGreaterThanEqualOrderByHearingDateAdAsc(
                            cc.getCourtId(), cc.getCaseNoInternal(), today)) {
                if (createMatch(cc, h, HearingSource.DAILY)) {
                    created++;
                }
            }
            for (WeeklyHearing h : weeklyRepository
                    .findByCourtIdAndCaseNoInternalAndHearingDateAdGreaterThanEqualOrderByHearingDateAdAsc(
                            cc.getCourtId(), cc.getCaseNoInternal(), today)) {
                if (createMatch(cc, h, HearingSource.WEEKLY)) {
                    created++;
                }
            }
        }
        if (created > 0) {
            log.info("Hearing matches created: {}", created);
        }
        dispatchPending();
        return created;
    }

    private boolean createMatch(ClientCase cc, DailyHearing h, HearingSource source) {
        return createMatch(cc, h.getCourtId(), h.getCaseNoInternal(), h.getHearingDateBs(),
                h.getHearingDateAd(), h.getBench(), h.getSerialNo(), h.getJudgeName(),
                h.getOrderType(), h.getSubject(), h.getPlaintiff(), h.getDefendant(),
                h.getCaseNoBs(), source);
    }

    private boolean createMatch(ClientCase cc, WeeklyHearing h, HearingSource source) {
        return createMatch(cc, h.getCourtId(), h.getCaseNoInternal(), h.getHearingDateBs(),
                h.getHearingDateAd(), h.getBench(), h.getSerialNo(), h.getJudgeName(),
                h.getOrderType(), h.getSubject(), h.getPlaintiff(), h.getDefendant(),
                h.getCaseNoBs(), source);
    }

    private boolean createMatch(ClientCase cc, Integer courtId, String caseNoInternal,
                                String dateBs, LocalDate dateAd,
                                String bench, String serialNo,
                                String judgeName, String orderType, String subject,
                                String plaintiff, String defendant, String caseNoBs,
                                HearingSource source) {
        if (matchRepository.existsByClientCaseIdAndCourtIdAndHearingDateBs(
                cc.getId(), courtId, dateBs)) {
            return false;
        }
        HearingMatch m = new HearingMatch();
        m.setClientCaseId(cc.getId());
        m.setCourtId(courtId);
        m.setCaseNoInternal(caseNoInternal);
        m.setHearingDateBs(dateBs);
        m.setHearingDateAd(dateAd);
        m.setBench(bench);
        m.setSerialNo(serialNo);
        m.setJudgeName(judgeName);
        m.setOrderType(orderType);
        m.setSubject(subject);
        m.setPlaintiff(plaintiff);
        m.setDefendant(defendant);
        m.setCaseNoBs(caseNoBs);
        m.setSource(source);
        m.setMatchedAt(LocalDateTime.now());
        m.setNotified(false);
        matchRepository.save(m);
        return true;
    }

    // One-time startup enrichment for matches created before the detail columns existed.
    @PostConstruct
    @Transactional
    public void backfillLegacyMatches() {
        int updated = 0;
        for (HearingMatch m : matchRepository.findBySubjectIsNull()) {
            var daily = dailyRepository.findByCourtIdAndCaseNoInternalAndHearingDateBs(
                    m.getCourtId(), m.getCaseNoInternal(), m.getHearingDateBs());
            if (daily.isPresent()) {
                DailyHearing h = daily.get();
                m.setSubject(h.getSubject());
                m.setPlaintiff(h.getPlaintiff());
                m.setDefendant(h.getDefendant());
                m.setCaseNoBs(h.getCaseNoBs());
                m.setBench(h.getBench());
                m.setSerialNo(h.getSerialNo());
                matchRepository.save(m);
                updated++;
                continue;
            }
            var weekly = weeklyRepository.findByCourtIdAndCaseNoInternalAndHearingDateBs(
                    m.getCourtId(), m.getCaseNoInternal(), m.getHearingDateBs());
            if (weekly.isPresent()) {
                WeeklyHearing h = weekly.get();
                m.setSubject(h.getSubject());
                m.setPlaintiff(h.getPlaintiff());
                m.setDefendant(h.getDefendant());
                m.setCaseNoBs(h.getCaseNoBs());
                m.setBench(h.getBench());
                m.setSerialNo(h.getSerialNo());
                matchRepository.save(m);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Backfilled {} legacy hearing match(es) with row details", updated);
        }
    }

    /**
     * All matches for a client case, newest first.
     */
    public List<HearingMatch> findByClientCaseId(Long clientCaseId) {
        return matchRepository.findByClientCaseIdOrderByHearingDateAdDesc(clientCaseId);
    }

    /**
     * Every match awaiting notification dispatch.
     */
    public List<HearingMatch> findUnnotified() {
        return matchRepository.findByNotifiedFalse();
    }

    // At-least-once: mark notified only after a successful dispatch, so failures are retried.
    private void dispatchPending() {
        for (HearingMatch m : matchRepository.findByNotifiedFalse()) {
            try {
                dispatcher.dispatch(
                        String.valueOf(m.getClientCaseId()),
                        "Hearing scheduled: " + m.getHearingDateBs(),
                        "Court " + m.getCourtId() + " · case " + m.getHearingDateBs()
                                + " · judge " + m.getJudgeName()
                                + (m.getOrderType() != null ? " · " + m.getOrderType() : ""));
                m.setNotified(true);
                matchRepository.save(m);
            } catch (Exception e) {
                log.warn("Notification dispatch failed for match {} — will retry: {}",
                        m.getId(), e.getMessage());
            }
        }
    }
}
