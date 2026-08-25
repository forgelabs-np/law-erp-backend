package com.lawfirm.erp.common.constant;

/**
 * Single source of truth for system role codes.
 * Used by DataInitializer, FirmServiceImpl, PermissionEvaluator, and controllers.
 * Never hardcode role code strings elsewhere — reference these constants.
 */
public final class RoleCode {

    private RoleCode() {}

    public static final String SUPER_ADMIN  = "SUPER_ADMIN";
    public static final String FIRM_ADMIN   = "FIRM_ADMIN";
    public static final String ADVOCATE     = "ADVOCATE";
    public static final String PARALEGAL    = "PARALEGAL";
    public static final String CLIENT       = "CLIENT";

    /** All system role codes in seed order. */
    public static final String[] ALL = {
            SUPER_ADMIN, FIRM_ADMIN, ADVOCATE, PARALEGAL, CLIENT
    };
}
