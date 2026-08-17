package com.lawfirm.erp.modules.scraper.parser;

import com.lawfirm.erp.modules.scraper.converter.DevanagariConverter;
import com.lawfirm.erp.modules.scraper.dto.CaseDetailResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// case_process_detail page: header h2 carries the case numbers, dl blocks the case info,
// then three record_display tables — तारेख विवरण (dates), वादी/प्रतिवादी (parties),
// पेशी विवरण (hearing history with judge + order). "भेटिएन" means not found.
@Component
public class CaseDetailParser {

    private static final Pattern CASE_NO_HEADER = Pattern.compile("मुद्दा\\s*([^\\s<-]+-[^\\s<-]+-[^\\s<-]+)");
    private static final Pattern REG_NO = Pattern.compile("रजिष्ट्रेशन\\s*नं\\s*[:：]\\s*([^<]+)");
    private static final String NOT_FOUND = "भेटिएन";

    public CaseDetailResponse parse(String html, Integer courtId) {
        if (html == null || html.isBlank() || html.contains(NOT_FOUND)) {
            return CaseDetailResponse.builder()
                    .found(false).courtId(courtId).hearings(List.of()).build();
        }
        Document doc = Jsoup.parse(html);

        String caseNoBs = null;
        String caseNoInternal = null;
        for (Element h2 : doc.select("h2")) {
            Matcher cm = CASE_NO_HEADER.matcher(h2.text());
            if (caseNoBs == null && cm.find()) {
                caseNoBs = clean(DevanagariConverter.toArabic(cm.group(1)));
            }
            Matcher rm = REG_NO.matcher(h2.text());
            if (caseNoBs != null && rm.find()) {
                caseNoInternal = clean(DevanagariConverter.toArabic(rm.group(1)));
            }
        }

        Info info = new Info();
        for (Element dl : doc.select("dl")) {
            Elements dts = dl.select("dt");
            Elements dds = dl.select("dd");
            for (int i = 0; i < dts.size(); i++) {
                String key = clean(dts.get(i).text());
                String value = i < dds.size() ? clean(dds.get(i).text()) : null;
                if (key == null) continue;
                switch (key) {
                    case "दर्ता मिति :" -> info.registrationDate = toArabic(value);
                    case "मुद्दाको किसिम :" -> info.caseType = value;
                    case "मुद्दाको बिषय :" -> info.subject = value;
                    case "फाँट :" -> info.division = toArabic(value);
                    case "पेशी चढेको संख्या :" -> info.hearingCount = toArabic(value);
                    case "मुद्दाको स्थिति:" -> info.status = value;
                    case "फैसला मिति:" -> info.verdictDate = value;
                    case "फैसला गर्ने मा. न्यायाधीश:" -> info.verdictJudge = value;
                    default -> {
                    }
                }
            }
        }

        List<CaseDetailResponse.HearingEntry> tarekhe = new ArrayList<>();
        List<CaseDetailResponse.Party> plaintiffs = new ArrayList<>();
        List<CaseDetailResponse.Party> defendants = new ArrayList<>();
        List<CaseDetailResponse.HearingEntry> hearings = new ArrayList<>();

        for (Element table : doc.select("table.record_display")) {
            String heading = table.text();
            if (heading.contains("प्रतिवादीहरु")) {
                parseParties(table, defendants);
            } else if (heading.contains("वादीहरु")) {
                parseParties(table, plaintiffs);
            } else if (heading.contains("तारेख मिति")) {
                parseDateTable(table, tarekhe);
            } else if (heading.contains("पेशी मिति")) {
                parseHearingTable(table, hearings);
            }
        }

        return CaseDetailResponse.builder()
                .found(true)
                .courtId(courtId)
                .caseNoBs(caseNoBs)
                .caseNoInternal(caseNoInternal)
                .registrationDate(info.registrationDate)
                .caseType(info.caseType)
                .subject(info.subject)
                .division(info.division)
                .status(info.status)
                .verdictDate(info.verdictDate)
                .verdictJudge(info.verdictJudge)
                .hearingCount(info.hearingCount)
                .plaintiffs(plaintiffs)
                .defendants(defendants)
                .hearings(hearings)
                .tarekhe(tarekhe)
                .build();
    }

    private void parseParties(Element table, List<CaseDetailResponse.Party> out) {
        for (Element tr : table.select("tr")) {
            List<Element> tds = tr.select("td");
            if (tds.size() < 2) continue;
            String name = clean(tds.get(0).text());
            String address = clean(tds.get(1).text());
            if (name != null) {
                out.add(CaseDetailResponse.Party.builder().name(name).address(address).build());
            }
        }
    }

    private void parseDateTable(Element table, List<CaseDetailResponse.HearingEntry> out) {
        for (Element tr : table.select("tr")) {
            List<Element> tds = tr.select("td");
            if (tds.size() < 2) continue;
            String date = clean(DevanagariConverter.toArabic(tds.get(0).text()));
            String type = clean(tds.get(1).text());
            if (date != null) {
                out.add(CaseDetailResponse.HearingEntry.builder().dateBs(date).type(type).build());
            }
        }
    }

    private void parseHearingTable(Element table, List<CaseDetailResponse.HearingEntry> out) {
        for (Element tr : table.select("tr")) {
            List<Element> tds = tr.select("td");
            if (tds.size() < 5) continue;
            out.add(CaseDetailResponse.HearingEntry.builder()
                    .dateBs(clean(DevanagariConverter.toArabic(tds.get(0).text())))
                    .type(clean(tds.get(1).text()))
                    .division(clean(DevanagariConverter.toArabic(tds.get(2).text())))
                    .judge(clean(tds.get(3).text()))
                    .order(clean(tds.get(4).text()))
                    .build());
        }
    }

    private String clean(String s) {
        if (s == null) return null;
        String t = s.replace('\u00A0', ' ').trim();
        return t.isEmpty() ? null : t;
    }

    private String toArabic(String s) {
        return clean(DevanagariConverter.toArabic(s));
    }

    private static class Info {
        String registrationDate;
        String caseType;
        String subject;
        String division;
        String hearingCount;
        String status;
        String verdictDate;
        String verdictJudge;
    }
}
