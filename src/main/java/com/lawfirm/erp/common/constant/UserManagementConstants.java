package com.lawfirm.erp.common.constant;

public final class UserManagementConstants {

    private UserManagementConstants() {}

    // UserManagementController
    public static final String LIST_USERS_SUMMARY = "List all users in the firm";
    public static final String LIST_USERS_DESCRIPTION = "Returns firm admins (FIRM), employees (FIRM_USER), and clients (CLIENT). Filter by userType, roleId, or isActive status.";

    public static final String SEARCH_USERS_SUMMARY = "Search users by name, email, username or mobile";
    public static final String SEARCH_USERS_DESCRIPTION = "Case-insensitive partial match across all users in the firm.";

    public static final String GET_PROFILE_SUMMARY = "Get full profile of a user";
    public static final String GET_PROFILE_DESCRIPTION = "Returns basic info + role + all permissions grouped by module + last 10 audit entries + monthly action count.";

    public static final String GET_PERMISSIONS_SUMMARY = "Get all permissions for a user";
    public static final String GET_PERMISSIONS_DESCRIPTION = "Shows exactly what this user can do, in a flat list and grouped by module.";

    public static final String GET_ACTIVITY_SUMMARY = "Get activity timeline for a user";
    public static final String GET_ACTIVITY_DESCRIPTION = "Paginated audit log for a specific user within this firm. Supports date range filtering.";

    public static final String RESET_PASSWORD_SUMMARY = "Reset a user's password";
    public static final String RESET_PASSWORD_DESCRIPTION = "Firm admin resets password for any user in their firm. Immediately invalidates the user's existing JWT — they must re-login.";

    public static final String DELETE_USER_SUMMARY = "Delete user";
    public static final String DELETE_USER_DESCRIPTION = "Soft-deletes a user by deactivating their account";

    public static final String BULK_DEACTIVATE_SUMMARY = "Deactivate multiple users at once";
    public static final String BULK_DEACTIVATE_DESCRIPTION = "Deactivates each user and invalidates their JWT. Returns per-user success/failure details. Skips: yourself, already inactive users.";

    public static final String BULK_ROLE_CHANGE_SUMMARY = "Reassign role for multiple users at once";
    public static final String BULK_ROLE_CHANGE_DESCRIPTION = "Changes the role for all given users. Validates role belongs to this firm. Invalidates JWT for all affected users. Returns per-user success/failure details.";

    // GlobalDashboardController
    public static final String GET_DASHBOARD_SUMMARY = "Get global dashboard";
    public static final String GET_DASHBOARD_DESCRIPTION = "Aggregated stats across users, firms, case management, scraper, and recent activity. FIRM_ADMIN sees firm-scoped data; SUPER_ADMIN sees all firms.";
}
