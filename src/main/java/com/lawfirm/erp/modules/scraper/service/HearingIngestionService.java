package com.lawfirm.erp.modules.scraper.service;

import com.lawfirm.erp.modules.scraper.dto.HearingRecord;
import com.lawfirm.erp.modules.scraper.entity.DailyHearing;
import com.lawfirm.erp.modules.scraper.entity.WeeklyHearing;
import com.lawfirm.erp.modules.scraper.repository.DailyHearingRepository;
import com.lawfirm.erp.modules.scraper.repository.WeeklyHearingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

// Idempotent upsert keyed on (courtId, caseNoInternal, hearingDateBs) — re-scraping a day
// replaces rows instead of duplicating.
@Slf4j
@Service
@RequiredArgsConstructor
public class HearingIngestionService {

    private final DailyHearingRepository dailyRepository;
    private final WeeklyHearingRepository weeklyRepository;

    @Transactional
    public int upsertDaily(List<HearingRecord> records) {
        int processed = 0;
        for (HearingRecord r : records) {
            if (r.getCaseNoInternal() == null || r.getHearingDateBs() == null) continue;
            DailyHearing h = dailyRepository
                    .findByCourtIdAndCaseNoInternalAndHearingDateBs(
                            r.getCourtId(), r.getCaseNoInternal(), r.getHearingDateBs())
                    .orElseGet(DailyHearing::new);
            apply(h, r);
            dailyRepository.save(h);
            processed++;
        }
        if (processed > 0) {
            log.info("Daily hearings upserted: {} rows", processed);
        }
        return processed;
    }

    @Transactional
    public int upsertWeekly(List<HearingRecord> records) {
        int processed = 0;
        for (HearingRecord r : records) {
            if (r.getCaseNoInternal() == null || r.getHearingDateBs() == null) continue;
            WeeklyHearing h = weeklyRepository
                    .findByCourtIdAndCaseNoInternalAndHearingDateBs(
                            r.getCourtId(), r.getCaseNoInternal(), r.getHearingDateBs())
                    .orElseGet(WeeklyHearing::new);
            apply(h, r);
            weeklyRepository.save(h);
            processed++;
        }
        if (processed > 0) {
            log.info("Weekly hearings upserted: {} rows", processed);
        }
        return processed;
    }

    private void apply(DailyHearing h, HearingRecord r) {
        h.setCourtId(r.getCourtId());
        h.setHearingDateBs(r.getHearingDateBs());
        h.setHearingDateAd(r.getHearingDateAd());
        h.setCaseNoBs(r.getCaseNoBs());
        h.setCaseNoInternal(r.getCaseNoInternal());
        h.setBench(r.getBench());
        h.setSerialNo(r.getSerialNo());
        h.setJudgeName(r.getJudgeName());
        h.setSubject(r.getSubject());
        h.setPlaintiff(r.getPlaintiff());
        h.setDefendant(r.getDefendant());
        h.setOrderType(r.getOrderType());
        h.setScrapedDate(LocalDate.now());
    }

    private void apply(WeeklyHearing h, HearingRecord r) {
        h.setCourtId(r.getCourtId());
        h.setHearingDateBs(r.getHearingDateBs());
        h.setHearingDateAd(r.getHearingDateAd());
        h.setCaseNoBs(r.getCaseNoBs());
        h.setCaseNoInternal(r.getCaseNoInternal());
        h.setBench(r.getBench());
        h.setSerialNo(r.getSerialNo());
        h.setJudgeName(r.getJudgeName());
        h.setSubject(r.getSubject());
        h.setPlaintiff(r.getPlaintiff());
        h.setDefendant(r.getDefendant());
        h.setOrderType(r.getOrderType());
        h.setScrapedDate(LocalDate.now());
    }
}
