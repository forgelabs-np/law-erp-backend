package com.lawfirm.erp.common.constant;

public final class FirmConstants {

    private FirmConstants() {}

    // ClientController
    public static final String CREATE_CLIENT_SUMMARY = "Create client";
    public static final String GET_ALL_CLIENTS_SUMMARY = "Get all clients (paginated)";
    public static final String GET_CLIENT_BY_ID_SUMMARY = "Get client by ID";
    public static final String TOGGLE_CLIENT_PORTAL_SUMMARY = "Enable/disable client portal access";

    // EmployeeController
    public static final String CREATE_EMPLOYEE_SUMMARY = "Create employee";
    public static final String GET_ALL_EMPLOYEES_SUMMARY = "Get all employees (paginated)";
    public static final String GET_EMPLOYEE_BY_ID_SUMMARY = "Get employee by ID";
    public static final String UPDATE_EMPLOYEE_SUMMARY = "Update employee";
    public static final String UPDATE_EMPLOYEE_ROLE_SUMMARY = "Update employee role";
    public static final String TOGGLE_EMPLOYEE_STATUS_SUMMARY = "Toggle employee status";

    // FirmProfileController
    public static final String GET_FIRM_PROFILE_SUMMARY = "Get firm profile";
    public static final String UPDATE_FIRM_PROFILE_SUMMARY = "Update firm profile";

    // FirmModuleController
    public static final String GET_MY_ENABLED_MODULES_SUMMARY = "Get enabled modules for my firm";

    // FirmModuleAdminController (Super Admin)
    public static final String ENABLE_MODULE_FOR_FIRM_SUMMARY = "Enable/disable a module for a firm";
    public static final String GET_FIRM_MODULES_STATUS_SUMMARY = "Get all modules with status for a firm";

    // FirmAdminController (Super Admin)
    public static final String CREATE_FIRM_SUMMARY = "Create a new firm with Firm Admin";
    public static final String GET_ALL_FIRM_ADMINS_SUMMARY = "Get all firm admins across all firms";
    public static final String GET_FIRM_ADMINS_BY_FIRM_SUMMARY = "Get all firm admins for a specific firm";
    public static final String GET_FIRM_ADMIN_BY_ID_SUMMARY = "Get firm admin by ID";
    public static final String TOGGLE_FIRM_ADMIN_STATUS_SUMMARY = "Toggle firm admin status (activate/deactivate)";

    // Firm lifecycle (Super Admin)
    public static final String SUSPEND_FIRM_SUMMARY = "Suspend a firm — blocks all firm users from accessing the system";
    public static final String ACTIVATE_FIRM_SUMMARY = "Activate a suspended or trial firm";
    public static final String EXTEND_TRIAL_SUMMARY = "Extend a trial firm's expiry date";
    public static final String CONVERT_TO_PERMANENT_SUMMARY = "Convert a trial firm to a permanent (paid) firm";

    // FirmConfigController
    public static final String GET_FIRM_CONFIG_SUMMARY = "Get firm config values";
    public static final String GET_FIRM_CONFIG_DESCRIPTION = "Returns brand colors, email footer, timezone, etc.";
    public static final String UPDATE_FIRM_CONFIG_SUMMARY = "Update firm config values";
    public static final String UPDATE_FIRM_CONFIG_DESCRIPTION = "Bulk update brand colors, email footer, timezone, etc.";

    // FirmEmailConfigController
    public static final String GET_EMAIL_CONFIG_SUMMARY = "Get firm email config";
    public static final String GET_EMAIL_CONFIG_DESCRIPTION = "Returns SMTP config WITHOUT the password. smtpPasswordSet indicates if a password was configured.";
    public static final String SAVE_EMAIL_CONFIG_SUMMARY = "Create or update firm email config";
    public static final String SAVE_EMAIL_CONFIG_DESCRIPTION = "Set SMTP settings. Use smtpPassword = \"__UNCHANGED__\" to keep existing password without resending it.";
    public static final String TEST_EMAIL_CONNECTION_SUMMARY = "Test SMTP connection";
    public static final String TEST_EMAIL_CONNECTION_DESCRIPTION = "Attempts to connect to the configured SMTP server. Updates testedAt and testPassed fields.";
    public static final String DELETE_EMAIL_CONFIG_SUMMARY = "Delete firm email config";
    public static final String DELETE_EMAIL_CONFIG_DESCRIPTION = "Removes the firm's SMTP config. All emails will use platform global SMTP.";

    // FirmRoleController
    public static final String GET_FIRM_ROLES_SUMMARY = "Get all roles for this firm";
    public static final String GET_FIRM_ROLES_DESCRIPTION = "Returns firm-scoped roles only. System templates are excluded. These are the roles firm admin can assign to employees.";
    public static final String GET_ROLE_PERMISSIONS_SUMMARY = "Get permissions for a firm role";
    public static final String GET_ROLE_PERMISSIONS_DESCRIPTION = "Returns two lists: (1) currentPermissions — what this role currently has. (2) availablePermissions — everything the system ceiling allows, with 'assigned' flag showing which are active. Use availablePermissions to build the checkbox UI for editing.";
    public static final String GET_ROLE_USERS_SUMMARY = "Get users assigned to this role";
    public static final String GET_ROLE_USERS_DESCRIPTION = "Lists all users within the firm who hold this role.";
    public static final String UPDATE_ROLE_PERMISSIONS_SUMMARY = "Update permissions for a firm role";
    public static final String UPDATE_ROLE_PERMISSIONS_DESCRIPTION = "Replaces all permissions on a firm-scoped role. Ceiling enforced — cannot assign permissions beyond what the system role allows. All users holding this role will have their JWT invalidated immediately and must re-login to get the updated permissions.";

    // Firm Admin role CRUD
    public static final String CREATE_FIRM_ROLE_SUMMARY = "Create a custom role";
    public static final String CREATE_FIRM_ROLE_DESCRIPTION = "Firm Admin creates a custom role within their firm. Role starts with no permissions — assign them via the permissions endpoint. Cannot create FIRM_ADMIN roles.";
    public static final String DELETE_FIRM_ROLE_SUMMARY = "Delete a custom role";
    public static final String DELETE_FIRM_ROLE_DESCRIPTION = "Deletes a firm-scoped custom role. Cannot delete FIRM_ADMIN or system roles. Role must have zero assigned users.";
    public static final String TOGGLE_FIRM_ROLE_SUMMARY = "Toggle role active status";
    public static final String TOGGLE_FIRM_ROLE_DESCRIPTION = "Enable or disable a custom firm role. Cannot toggle FIRM_ADMIN role.";
}
