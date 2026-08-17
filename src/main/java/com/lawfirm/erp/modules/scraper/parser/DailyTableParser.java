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

// Daily page: skip the judge-count summary table; read the date from the "मिति <date> को"
// title; per-bench sections = header table (इजलाश N + judge in td.judge) + record_display
// table whose last column (आदेश फैसलाको किसिम) is the outcome.
@Component
public class DailyTableParser {

    private static final Pattern BENCH_HEADER = Pattern.compile("इजलाश\\s*(\\d+)");
    private static final Pattern PAGE_DATE = Pattern.compile("मिति\\s*([\\d०-९-]+)\\s*को");

    public List<HearingRecord> parse(String html, Integer courtId) {
        List<HearingRecord> records = new ArrayList<>();
        if (html == null || html.isBlank()) return records;

        String currentJudge = null;
        String currentBench = null;
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
                String[] header = extractBenchHeader(el);
                if (header != null) {
                    currentBench = header[0];
                    currentJudge = header[1];
                }
                continue;
            }
            if (isDetailTable(el)) {
                parseRows(el, courtId, currentJudge, currentBench, pageDateBs, records);
            }
        }
        return records;
    }

    /**
     * From a bench header table: [bench ("1"), judge name]. Prefers the td.judge cell for the
     * judge; the इजलाश N cell is the fallback for both when no judge cell exists.
     */
    private String[] extractBenchHeader(Element table) {
        String bench = null;
        String judge = null;
        Element judgeCell = table.selectFirst("td.judge");
        if (judgeCell != null) {
            judge = clean(judgeCell.text());
        }
        for (Element td : table.select("td")) {
            Matcher m = BENCH_HEADER.matcher(td.text());
            if (m.find()) {
                bench = m.group(1);
                if (judge == null) {
                    judge = clean(td.text());
                }
                break;
            }
        }
        if (bench == null && judge == null) return null;
        return new String[]{bench, judge};
    }

    private void parseRows(Element table, Integer courtId, String judge, String bench, String dateBs,
                           List<HearingRecord> out) {
        for (Element tr : table.select("tr")) {
            if (tr.select("th").size() > 0) continue; // header row
            List<Element> cells = tr.select("td");
            if (cells.size() < 6) continue; // footer (इजलास अधिकृत) rows are single cells

            String[] nums = CaseNumberExtractor.split(cells.get(1).text());
            String serialNo = clean(DevanagariConverter.toArabic(cells.get(0).text()));
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
                    .bench(bench)
                    .serialNo(serialNo)
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
