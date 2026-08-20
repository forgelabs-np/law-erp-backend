package com.lawfirm.erp.common.constant;

public final class FirmConstants {

    private FirmConstants() {}

    // ClientController
    public static final String CREATE_CLIENT_SUMMARY = "Create a new client";
    public static final String GET_ALL_CLIENTS_SUMMARY = "Get all clients";
    public static final String GET_CLIENT_BY_ID_SUMMARY = "Get client by ID";
    public static final String TOGGLE_CLIENT_PORTAL_SUMMARY = "Toggle client portal access";

    // EmployeeController
    public static final String CREATE_EMPLOYEE_SUMMARY = "Create a new employee";
    public static final String GET_ALL_EMPLOYEES_SUMMARY = "Get all employees";
    public static final String GET_EMPLOYEE_BY_ID_SUMMARY = "Get employee by ID";
    public static final String UPDATE_EMPLOYEE_SUMMARY = "Update employee details";
    public static final String UPDATE_EMPLOYEE_ROLE_SUMMARY = "Update employee role";
    public static final String TOGGLE_EMPLOYEE_STATUS_SUMMARY = "Toggle employee active status";

    // FirmConfigController
    public static final String CREATE_FIRM_SUMMARY = "Create a new firm";

    // FirmAdminController
    public static final String GET_ALL_FIRM_ADMINS_SUMMARY = "Get all firm admins";
    public static final String GET_FIRM_ADMINS_BY_FIRM_SUMMARY = "Get firm admins by firm";
    public static final String GET_FIRM_ADMIN_BY_ID_SUMMARY = "Get firm admin by ID";
    public static final String TOGGLE_FIRM_ADMIN_STATUS_SUMMARY = "Toggle firm admin status";

    // FirmProfileController
    public static final String GET_FIRM_PROFILE_SUMMARY = "Get firm profile";
    public static final String UPDATE_FIRM_PROFILE_SUMMARY = "Update firm profile";

    // FirmModuleController
    public static final String ENABLE_MODULE_SUMMARY = "Enable/configure module for firm";
    public static final String GET_MODULE_CONFIG_SUMMARY = "Get module configuration";
    public static final String GET_FIRM_MODULES_SUMMARY = "Get all firm modules";
    public static final String GET_MY_ENABLED_MODULES_SUMMARY = "Get my enabled modules";

    // FirmEmailConfigController
    public static final String SAVE_EMAIL_CONFIG_SUMMARY = "Save email configuration";
    public static final String TEST_EMAIL_CONNECTION_SUMMARY = "Test email connection";
    public static final String DELETE_EMAIL_CONFIG_SUMMARY = "Delete email configuration";

    // FirmRoleController
    public static final String GET_FIRM_ROLES_SUMMARY = "Get all firm roles";
    public static final String GET_ROLE_PERMISSIONS_SUMMARY = "Get role permissions";
    public static final String UPDATE_ROLE_PERMISSIONS_SUMMARY = "Update role permissions";
    public static final String GET_ROLE_USERS_SUMMARY = "Get users assigned to role";
}
