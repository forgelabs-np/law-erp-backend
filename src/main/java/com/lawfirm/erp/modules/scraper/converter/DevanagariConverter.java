package com.lawfirm.erp.modules.scraper.converter;

// Court-site numerals are Devanagari; the DB stores Arabic digits. Non-digits pass through.
public final class DevanagariConverter {

    private static final char[] DEVANAGARI = {'०', '१', '२', '३', '४', '५', '६', '७', '८', '९'};

    private DevanagariConverter() {
    }

    public static String toArabic(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int idx = indexOf(c);
            out.append(idx >= 0 ? (char) ('0' + idx) : c);
        }
        return out.toString();
    }

    public static String toDevanagari(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out.append(c >= '0' && c <= '9' ? DEVANAGARI[c - '0'] : c);
        }
        return out.toString();
    }

    public static boolean containsDevanagariDigit(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            if (indexOf(s.charAt(i)) >= 0) return true;
        }
        return false;
    }

    private static int indexOf(char c) {
        for (int i = 0; i < DEVANAGARI.length; i++) {
            if (DEVANAGARI[i] == c) return i;
        }
        return -1;
    }
}
