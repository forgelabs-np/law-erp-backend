package com.lawfirm.erp.common.constant;

public final class RoleCode {

    private RoleCode() {}

    public static final String SUPER_ADMIN  = "SUPER_ADMIN";
    public static final String FIRM_ADMIN   = "FIRM_ADMIN";
    public static final String ADVOCATE     = "ADVOCATE";
    public static final String PARALEGAL    = "PARALEGAL";
    public static final String CLIENT       = "CLIENT";

    public static final String[] ALL = {
            SUPER_ADMIN, FIRM_ADMIN, ADVOCATE, PARALEGAL, CLIENT
    };
}
