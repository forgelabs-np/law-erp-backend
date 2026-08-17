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
 * Daily cause list (real structure):
 *   1. a summary table of per-judge counts — skipped;
 *   2. a "मिति <date> को दैनिक पेशि सुची" title — carries the hearing date;
 *   3. per-bench sections: a plain header table with "इजलाश N" and the judge name in a
 *      td.judge cell, followed by a `record_display` detail table (10 columns) whose last
 *      column (आदेश फैसलाको किसिम) is the order/outcome.
 */
@Component
public class DailyTableParser {

    private static final Pattern JUDGE_HEADER = Pattern.compile("इजलाश\\s*\\d+");
    private static final Pattern PAGE_DATE = Pattern.compile("मिति\\s*([\\d०-९-]+)\\s*को");

    public List<HearingRecord> parse(String html, Integer courtId) {
        List<HearingRecord> records = new ArrayList<>();
        if (html == null || html.isBlank()) return records;

        String currentJudge = null;
        String pageDateBs = null;
        for (Element el : Jsoup.parse(html).getAllElements()) {
            if (el.tagName().equals("span")) {
                Matcher m = PAGE_DATE.matcher(el.text());
                if (m.find()) {
                    pageDateBs = DevanagariConverter.toArabic(m.group(1).trim());
                }
                continue;
            }
            if (!el.tagName().equals("table")) continue;

            if (!el.hasClass("record_display")) {
                String judge = extractJudge(el);
                if (judge != null) {
                    currentJudge = judge;
                }
                continue;
            }
            if (isDetailTable(el)) {
                parseRows(el, courtId, currentJudge, pageDateBs, records);
            }
        }
        return records;
    }

    /** Judge name from a bench header table: prefer the td.judge cell, else the इजलाश N cell. */
    private String extractJudge(Element table) {
        Element judgeCell = table.selectFirst("td.judge");
        if (judgeCell != null) {
            String name = judgeCell.text().replace('\u00A0', ' ').trim();
            if (!name.isEmpty()) return name;
        }
        for (Element td : table.select("td")) {
            if (JUDGE_HEADER.matcher(td.text()).find()) {
                String t = td.text().replace('\u00A0', ' ').trim();
                if (!t.isEmpty()) return t;
            }
        }
        return null;
    }

    private void parseRows(Element table, Integer courtId, String judge, String dateBs,
                           List<HearingRecord> out) {
        for (Element tr : table.select("tr")) {
            if (tr.select("th").size() > 0) continue; // header row
            List<Element> cells = tr.select("td");
            if (cells.size() < 6) continue; // footer (इजलास अधिकृत) rows are single cells

            String[] nums = CaseNumberExtractor.split(cells.get(1).text());
            String subject = clean(cells.get(3).text());
            String plaintiff = clean(cells.get(4).text());
            String defendant = clean(cells.get(5).text());
            String orderType = cells.size() > 9 ? clean(cells.get(9).text()) : null;

            out.add(HearingRecord.builder()
                    .courtId(courtId)
                    .hearingDateBs(dateBs)
                    .hearingDateAd(dateBs != null ? NepaliDateUtil.bsToAd(dateBs) : null)
                    .caseNoBs(nums[0])
                    .caseNoInternal(nums[1])
                    .judgeName(judge)
                    .subject(subject)
                    .plaintiff(plaintiff)
                    .defendant(defendant)
                    .orderType(orderType)
                    .source(HearingSource.DAILY)
                    .build());
        }
    }

    /** Detail tables carry a "मुद्दा" header; the summary (judge counts) table does not. */
    private boolean isDetailTable(Element el) {
        for (Element tr : el.select("tr")) {
            List<Element> ths = tr.select("th");
            if (ths.isEmpty()) continue;
            for (Element th : ths) {
                if (th.text().contains("मुद्दा")) return true;
            }
        }
        return false;
    }

    private String clean(String s) {
        if (s == null) return null;
        String t = s.replace('\u00A0', ' ').trim();
        return t.isEmpty() ? null : t;
    }
}
