package com.lawfirm.erp.modules.scraper.service;

import com.lawfirm.erp.modules.scraper.config.ScraperProperties;
import com.lawfirm.erp.modules.scraper.entity.DailyHearing;
import com.lawfirm.erp.modules.scraper.entity.HearingMatch;
import com.lawfirm.erp.modules.scraper.entity.WeeklyHearing;
import com.lawfirm.erp.modules.scraper.repository.DailyHearingRepository;
import com.lawfirm.erp.modules.scraper.repository.HearingMatchRepository;
import com.lawfirm.erp.modules.scraper.repository.WeeklyHearingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Weekly offline snapshot: exports the previous Monday–Sunday window from the raw
 * daily + weekly tables into a single UTF-8 CSV (BOM-prefixed so Excel renders the
 * Devanagari text correctly; all fields quoted). The `matched` column marks rows that
 * appear in hearing_matches (i.e. one of our registered cases).
 *
 * The DB remains the system of record — this file is a convenience copy, regenerated
 * idempotently (same file name per week, temp-file + atomic rename).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HearingExportService {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String HEADER = "courtId,hearingDateBs,hearingDateAd,caseNoBs,caseNoInternal,"
            + "judgeName,subject,plaintiff,defendant,orderType,source,matched";

    private final DailyHearingRepository dailyRepository;
    private final WeeklyHearingRepository weeklyRepository;
    private final HearingMatchRepository matchRepository;
    private final ScraperProperties properties;

    /** Exports the previous completed Monday–Sunday week. */
    public Path exportLastWeek() {
        LocalDate monday = previousMonday(LocalDate.now());
        return export(monday, monday.plusDays(6));
    }

    public Path export(LocalDate monday, LocalDate sunday) {
        List<Row> rows = new ArrayList<>();
        for (DailyHearing h : dailyRepository
                .findByHearingDateAdBetweenOrderByCourtIdAscHearingDateAdAsc(monday, sunday)) {
            rows.add(Row.of(h.getCourtId(), h.getHearingDateBs(), h.getHearingDateAd(),
                    h.getCaseNoBs(), h.getCaseNoInternal(), h.getJudgeName(), h.getSubject(),
                    h.getPlaintiff(), h.getDefendant(), h.getOrderType(), "DAILY"));
        }
        for (WeeklyHearing h : weeklyRepository
                .findByHearingDateAdBetweenOrderByCourtIdAscHearingDateAdAsc(monday, sunday)) {
            rows.add(Row.of(h.getCourtId(), h.getHearingDateBs(), h.getHearingDateAd(),
                    h.getCaseNoBs(), h.getCaseNoInternal(), h.getJudgeName(), h.getSubject(),
                    h.getPlaintiff(), h.getDefendant(), h.getOrderType(), "WEEKLY"));
        }
        rows.sort(Comparator.comparing(Row::courtId)
                .thenComparing(Row::hearingDateAd, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Row::caseNoInternal, Comparator.nullsLast(Comparator.naturalOrder())));

        Set<String> matchedKeys = new HashSet<>();
        for (HearingMatch m : matchRepository.findByHearingDateAdBetween(monday, sunday)) {
            matchedKeys.add(key(m.getCourtId(), m.getCaseNoInternal(), m.getHearingDateBs()));
        }

        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF'); // UTF-8 BOM — Excel renders Devanagari correctly
        csv.append(HEADER).append('\n');
        for (Row r : rows) {
            csv.append(quote(r.courtId())).append(',')
                    .append(quote(r.hearingDateBs())).append(',')
                    .append(quote(r.hearingDateAd() != null ? r.hearingDateAd().toString() : "")).append(',')
                    .append(quote(r.caseNoBs())).append(',')
                    .append(quote(r.caseNoInternal())).append(',')
                    .append(quote(r.judgeName())).append(',')
                    .append(quote(r.subject())).append(',')
                    .append(quote(r.plaintiff())).append(',')
                    .append(quote(r.defendant())).append(',')
                    .append(quote(r.orderType())).append(',')
                    .append(quote(r.source())).append(',')
                    .append(quote(matchedKeys.contains(
                            key(r.courtId(), r.caseNoInternal(), r.hearingDateBs())) ? "Y" : "N"))
                    .append('\n');
        }

        Path dir = Path.of(properties.getExportDir());
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve("week-" + FILE_DATE.format(monday) + "_" + FILE_DATE.format(sunday) + ".csv");
            Path tmp = dir.resolve(target.getFileName() + ".tmp");
            Files.writeString(tmp, csv.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Weekly export written: {} ({} rows)", target, rows.size());
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Weekly export failed: " + e.getMessage(), e);
        }
    }

    private LocalDate previousMonday(LocalDate today) {
        LocalDate monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.equals(today) ? monday.minusWeeks(1) : monday;
    }

    private String key(Integer courtId, String caseNoInternal, String dateBs) {
        return courtId + "|" + caseNoInternal + "|" + dateBs;
    }

    private String quote(Object v) {
        if (v == null) return "\"\"";
        String s = String.valueOf(v).replace("\"", "\"\"");
        return '"' + s + '"';
    }

    private record Row(Integer courtId, String hearingDateBs, LocalDate hearingDateAd,
                       String caseNoBs, String caseNoInternal, String judgeName, String subject,
                       String plaintiff, String defendant, String orderType, String source) {
        static Row of(Integer courtId, String dateBs, LocalDate dateAd, String caseNoBs,
                      String internal, String judge, String subject, String plaintiff,
                      String defendant, String orderType, String source) {
            return new Row(courtId, dateBs, dateAd, caseNoBs, internal, judge, subject,
                    plaintiff, defendant, orderType, source);
        }
    }
}
