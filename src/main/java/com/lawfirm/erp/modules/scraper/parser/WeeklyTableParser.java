package com.lawfirm.erp.modules.scraper.parser;

import com.lawfirm.erp.modules.scraper.converter.DevanagariConverter;
import com.lawfirm.erp.modules.scraper.converter.NepaliDateUtil;
import com.lawfirm.erp.modules.scraper.dto.HearingRecord;
import com.lawfirm.erp.modules.scraper.enums.HearingSource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Weekly cause list: one response covers the Sun–Fri window, grouped by "पेशी मिति :<date>",
 * then a flat table per date (no per-judge sub-tables, no outcome column). Party names share
 * one "पक्ष || विपक्ष" column split on "||" / "‖".
 */
@Component
public class WeeklyTableParser {

    private static final Pattern DATE_HEADER = Pattern.compile("पेशी मिति\\s*:?\\s*([\\d०-९-]+)");
    private static final Pattern PARTY_SPLIT = Pattern.compile("\\s*(?:\\|\\||‖)\\s*");

    public List<HearingRecord> parse(String html, Integer courtId) {
        List<HearingRecord> records = new ArrayList<>();
        if (html == null || html.isBlank()) return records;

        String currentDateBs = null;
        for (Element el : Jsoup.parse(html).getAllElements()) {
            if (isDateHeader(el)) {
                Matcher m = DATE_HEADER.matcher(el.text());
                if (m.find()) {
                    currentDateBs = DevanagariConverter.toArabic(m.group(1).trim());
                }
                continue;
            }
            if (isRecordTable(el) && currentDateBs != null) {
                parseRows(el, courtId, currentDateBs, records);
            }
        }
        return records;
    }

    private void parseRows(Element table, Integer courtId, String dateBs, List<HearingRecord> out) {
        for (Element tr : table.select("tr")) {
            if (tr.select("th").size() > 0) continue; // header row
            List<Element> cells = tr.select("td");
            if (cells.size() < 5) continue;

            String[] nums = CaseNumberExtractor.split(cells.get(1).text());
            String[] party = splitParty(cells.get(4).text());
            String subject = clean(cells.get(3).text());

            out.add(HearingRecord.builder()
                    .courtId(courtId)
                    .hearingDateBs(dateBs)
                    .hearingDateAd(NepaliDateUtil.bsToAd(dateBs))
                    .caseNoBs(nums[0])
                    .caseNoInternal(nums[1])
                    .subject(subject)
                    .plaintiff(party[0])
                    .defendant(party[1])
                    .source(HearingSource.WEEKLY)
                    .build());
        }
    }

    private String[] splitParty(String cell) {
        if (cell == null || cell.isBlank()) return new String[]{null, null};
        String[] parts = PARTY_SPLIT.split(cell);
        if (parts.length == 0) return new String[]{null, null};
        if (parts.length == 1) return new String[]{parts[0].trim(), null};
        return new String[]{parts[0].trim(), parts[1].trim()};
    }

    private boolean isDateHeader(Element el) {
        String tag = el.tagName();
        return (tag.equals("h1") || tag.equals("h2") || tag.equals("h3") || tag.equals("h4"))
                && el.text().contains("पेशी मिति");
    }

    private boolean isRecordTable(Element el) {
        return el.tagName().equals("table") && el.hasClass("record_display");
    }

    private String clean(String s) {
        if (s == null) return null;
        String t = s.replace('\u00A0', ' ').trim();
        return t.isEmpty() ? null : t;
    }
}
