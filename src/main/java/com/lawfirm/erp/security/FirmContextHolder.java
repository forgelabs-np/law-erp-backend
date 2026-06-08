package com.lawfirm.erp.security;

import java.util.UUID;

public class FirmContextHolder {
    private static final ThreadLocal<UUID> FIRM_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> FIRM_CODE = new ThreadLocal<>();

    public static void set(UUID firmId, String firmCode) {
        FIRM_ID.set(firmId);
        FIRM_CODE.set(firmCode);
    }

    public static UUID getFirmId() {
        return FIRM_ID.get();
    }

    public static String getFirmCode() {
        return FIRM_CODE.get();
    }

    public static void clear() {
        FIRM_ID.remove();
        FIRM_CODE.remove();
    }

    public static boolean isSuperAdminMode() {
        return FIRM_ID.get() == null;
    }
}