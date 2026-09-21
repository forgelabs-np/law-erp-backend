package com.lawfirm.erp.modules.usermanagement.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable scope object built once per request from the authenticated user.
 * Every dashboard service receives this — no service can accidentally get
 * an unscoped firmId or a raw user filter.
 */
@Getter
@Builder
public class DashboardScope {

    /** Who is asking. */
    private final UUID userId;

    /** Which firm (null for SUPER_ADMIN). */
    private final UUID firmId;

    /** User type: SUPER_ADMIN, FIRM, FIRM_USER, CLIENT. */
    private final String userType;

    /** Role code: ADVOCATE, PARALEGAL, FIRM_ADMIN, CLIENT, etc. */
    private final String roleCode;

    /** Clock injection for testing. */
    private final LocalDateTime now;

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(userType);
    }

    public boolean isFirmAdmin() {
        return "FIRM".equals(userType);
    }

    public boolean isFirmUser() {
        return "FIRM_USER".equals(userType);
    }

    public boolean isClient() {
        return "CLIENT".equals(userType);
    }
}
