package com.lawfirm.erp.common.constant;

public final class SuperAdminConstants {

    private SuperAdminConstants() {}

    // SuperAdminController
    public static final String REGISTER_SUMMARY = "Register Super Admin (One-time only)";
    public static final String REGISTER_DESCRIPTION = "Creates the first super admin. Will fail if already exists.";

    public static final String LOGIN_SUMMARY = "Super Admin Login";
    public static final String LOGIN_DESCRIPTION = "Login for super admin - No lawFirmCode required";

    public static final String GET_USERS_SUMMARY = "Get all users with their roles";
    public static final String GET_USERS_DESCRIPTION = "User-first view for the super admin: returns every user with its role (name/code) and firm (code/name). Optional filters: userType, search, firmCode.";

    public static final String RESET_MFA_SUMMARY = "Reset user MFA";
    public static final String RESET_MFA_DESCRIPTION = "Super Admin resets a user's MFA authenticator, forcing them to set it up again on next login";

    // SuperAdminConfigController
    public static final String GET_GLOBAL_CONFIG_SUMMARY = "Get all global system config values";
    public static final String UPDATE_GLOBAL_CONFIG_SUMMARY = "Bulk upsert global system config values";
    public static final String DELETE_GLOBAL_CONFIG_SUMMARY = "Delete a global config value";
    public static final String GET_FIRM_CONFIG_SUMMARY = "Get all config values for a specific firm";
    public static final String UPDATE_FIRM_CONFIG_SUMMARY = "Bulk upsert config values for a specific firm";

    // SuperAdminAuditController
    public static final String GET_ALL_AUDIT_LOGS_SUMMARY = "Get all audit logs";
    public static final String GET_ALL_AUDIT_LOGS_DESCRIPTION = "Super Admin views all audit logs across all firms with optional filters";
    public static final String GET_FIRM_AUDIT_LOGS_SUMMARY = "Get audit logs for a firm";
    public static final String GET_FIRM_AUDIT_LOGS_DESCRIPTION = "Super Admin views audit logs for a specific firm";
    public static final String GET_USER_AUDIT_LOGS_SUMMARY = "Get audit logs for a user";
    public static final String GET_USER_AUDIT_LOGS_DESCRIPTION = "Super Admin views audit logs for a specific user across all firms";
    public static final String GET_ENTITY_HISTORY_SUMMARY = "Get entity history";
    public static final String GET_ENTITY_HISTORY_DESCRIPTION = "Super Admin views history for a specific entity across all firms";

    // Super Admin role override
    public static final String OVERRIDE_ROLE_PERMS_SUMMARY = "Override role permissions for any firm";
    public static final String OVERRIDE_ROLE_PERMS_DESCRIPTION = "Super Admin directly sets permissions on any firm-scoped role, bypassing all ceilings. No restrictions — Super Admin is platform-level.";

    // Super Admin firm-role visibility (delegation chain)
    public static final String GET_FIRM_ROLES_SUMMARY = "List a firm's roles with permissions";
    public static final String GET_FIRM_ROLES_DESCRIPTION = "Super Admin view of any firm's scoped roles: each role's current permissions and how many users hold it. Read-only — use the override endpoint to change permissions, or the template endpoints for platform-wide changes.";

    public static final String CREATE_FIRM_ROLE_SUMMARY = "Create a firm-scoped role on a firm's behalf";
    public static final String CREATE_FIRM_ROLE_DESCRIPTION = "Super Admin creates a custom role inside a specific firm using the same service as the Firm Admin path. A base system template (parentRoleId) is required so the role participates in ceiling checks. Permissions are assigned afterward via the override endpoint.";
}
