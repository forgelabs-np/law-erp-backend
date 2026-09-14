package com.lawfirm.erp.common.constant;

public final class MeConstants {

    private MeConstants() {}

    public static final String GET_ME_SUMMARY = "Get current user identity";
    public static final String GET_ME_DESCRIPTION = "Returns full identity for the logged-in user: profile, firm context, role, all permissions (flat + grouped by module). Frontend calls this once on app load to build the sidebar and permission checks.";
}
