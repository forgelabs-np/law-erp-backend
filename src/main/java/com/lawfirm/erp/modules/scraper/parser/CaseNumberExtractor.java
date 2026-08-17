package com.lawfirm.erp.modules.scraper.parser;

import com.lawfirm.erp.modules.scraper.converter.DevanagariConverter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a case-number cell like "०८१-C४-३८२७ (३९-०८१-३२०३०)" into its two forms:
 *   - caseNoBs       = "081-C4-3827"       (court-visible number)
 *   - caseNoInternal = "39-081-32030"      (court-scoped internal id, from the parenthesized form)
 * Both are normalized to Arabic digits.
 */
final class CaseNumberExtractor {

    private static final Pattern PAREN = Pattern.compile("\\(([^)]+)\\)");

    private CaseNumberExtractor() {
    }

    static String[] split(String cellText) {
        if (cellText == null) return new String[]{null, null};
        String t = cellText.trim();
        String internal = null;
        Matcher m = PAREN.matcher(t);
        if (m.find()) {
            internal = normalize(m.group(1));
        }
        String rest = t.replaceAll("\\([^)]*\\)", "").trim();
        String bs = null;
        if (!rest.isEmpty()) {
            String first = rest.split("\\s+")[0].trim();
            if (!first.isEmpty() && !first.equals("&nbsp;")) {
                bs = normalize(first);
            }
        }
        return new String[]{bs, internal};
    }

    private static String normalize(String s) {
        return DevanagariConverter.toArabic(s.trim().replace("\u00A0", " "));
    }
}
