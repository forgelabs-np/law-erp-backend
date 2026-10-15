package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.exception.BusinessRuleException;

public final class BrandColorValidator {

    private BrandColorValidator() {
    }

    /**
     * Whether a firm has chosen its own brand colors. A firm is "personal" as soon as either
     * hex is set; when neither is, the portal renders the app default theme instead.
     */
    public static boolean hasPersonalColors(String primary, String secondary) {
        return isSet(primary) || isSet(secondary);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Accepts {@code #rrggbb} or {@code rrggbb}, case-insensitive, exactly six hex
     * characters. Returns the normalized form with a leading {@code #}.
     *
     * @throws BusinessRuleException when the value is not a valid hex color (surfaces as 400)
     */
    public static String normalizeHex(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() != 6 || !s.matches("^[0-9a-fA-F]{6}$")) {
            throw new BusinessRuleException(
                    "Invalid brand color '" + raw + "'. Expected #rrggbb or rrggbb.");
        }
        return "#" + s.toLowerCase();
    }
}
