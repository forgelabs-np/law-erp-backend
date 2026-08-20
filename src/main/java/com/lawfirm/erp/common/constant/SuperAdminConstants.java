package com.lawfirm.erp.common.constant;

public final class SuperAdminConstants {

    private SuperAdminConstants() {}

    public static final String REGISTER_SUMMARY = "Register Super Admin (One-time only)";
    public static final String REGISTER_DESCRIPTION = "Creates the first super admin. Will fail if already exists.";

    public static final String LOGIN_SUMMARY = "Super Admin Login";
    public static final String LOGIN_DESCRIPTION = "Login for super admin - No lawFirmCode required";

    public static final String GET_USERS_SUMMARY = "Get all users with their roles";
    public static final String GET_USERS_DESCRIPTION = "User-first view for the super admin: returns every user with its role (name/code) and firm (code/name). Optional filters: userType, search, firmCode.";
}
