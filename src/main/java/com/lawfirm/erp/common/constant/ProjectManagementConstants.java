package com.lawfirm.erp.common.constant;

public final class ProjectManagementConstants {

    private ProjectManagementConstants() {}

    // ── Module ─────────────────────────────────────────────────────────────
    public static final String MODULE_CODE = "PROJECT_MANAGEMENT";
    public static final String MODULE_NAME = "Project Management";
    public static final String MODULE_DESC = "Manage client projects, credentials, and renewal tracking";

    // ── Swagger Tags ───────────────────────────────────────────────────────
    public static final String TAG_PROJECTS = "Project Management - Projects";
    public static final String TAG_CREDENTIALS = "Project Management - Credentials";
    public static final String TAG_RENEWALS = "Project Management - Renewals";
    public static final String TAG_RENEWAL_TYPES = "Project Management - Renewal Types";
    public static final String TAG_CLIENT_PORTAL = "Project Management - Client Portal";
    public static final String TAG_DASHBOARD = "Project Management - Dashboard";

    // ── Swagger Summaries ──────────────────────────────────────────────────
    public static final String CREATE_PROJECT = "Create a new project";
    public static final String LIST_PROJECTS = "List projects with filters and pagination";
    public static final String GET_PROJECT = "Get project details by project code";
    public static final String UPDATE_PROJECT = "Update project details";
    public static final String UPDATE_PROJECT_STATUS = "Change project status";

    public static final String ADD_CREDENTIAL = "Add a portal credential to a project";
    public static final String LIST_CREDENTIALS = "List credentials for a project";
    public static final String GET_CREDENTIAL = "Get credential details";
    public static final String UPDATE_CREDENTIAL = "Update a credential";
    public static final String DELETE_CREDENTIAL = "Soft delete a credential";
    public static final String REVEAL_PASSWORD = "Reveal encrypted password (audit-logged)";

    public static final String CREATE_RENEWAL = "Create a renewal (auto-generates instances)";
    public static final String LIST_RENEWALS = "List renewals for a project";
    public static final String GET_RENEWAL = "Get renewal details with instances";
    public static final String UPDATE_RENEWAL = "Update renewal template";
    public static final String UPDATE_RENEWAL_STATUS = "Change renewal status";
    public static final String UPDATE_INSTANCE_STATUS = "Update a renewal instance status";

    public static final String LIST_RENEWAL_TYPES = "List available renewal types";
    public static final String CREATE_RENEWAL_TYPE = "Create a custom renewal type";
    public static final String UPDATE_RENEWAL_TYPE = "Update a custom renewal type";
    public static final String DELETE_RENEWAL_TYPE = "Delete a custom renewal type";

    public static final String ADD_MEMBER = "Add a team member to a project";
    public static final String REMOVE_MEMBER = "Remove a team member from a project";
    public static final String LIST_MEMBERS = "List team members";

    public static final String CLIENT_LIST_PROJECTS = "List projects for current client";
    public static final String CLIENT_GET_PROJECT = "View project details (limited)";
    public static final String CLIENT_LIST_RENEWALS = "View renewal deadlines (limited)";

    public static final String PROJECT_DASHBOARD = "Project management dashboard stats";

    // ── Error Messages ─────────────────────────────────────────────────────
    public static final String PROJECT_NOT_FOUND = "Project not found";
    public static final String CREDENTIAL_NOT_FOUND = "Credential not found";
    public static final String RENEWAL_NOT_FOUND = "Renewal not found";
    public static final String RENEWAL_TYPE_NOT_FOUND = "Renewal type not found";
    public static final String MEMBER_NOT_FOUND = "Team member not found";
    public static final String CLIENT_NOT_FOUND = "Client user not found";
    public static final String CANNOT_DELETE_SYSTEM_TYPE = "Cannot delete system renewal types";
    public static final String OWNER_CANNOT_BE_REMOVED = "Cannot remove the project owner";
}
