package com.lawfirm.erp.common.constant;

public final class RbacConstants {

    private RbacConstants() {}

    // ModuleController
    public static final String UPSERT_MODULE_SUMMARY = "Create or update module";
    public static final String GET_ALL_MODULES_SUMMARY = "Get all modules";
    public static final String GET_ACTIVE_MODULES_SUMMARY = "Get active modules";
    public static final String GET_MODULE_BY_ID_SUMMARY = "Get module by ID";
    public static final String DELETE_MODULE_SUMMARY = "Delete module";
    public static final String TOGGLE_MODULE_SUMMARY = "Toggle module status";
    public static final String ASSIGN_PERMISSIONS_TO_MODULE_SUMMARY = "Assign permissions to module";

    // PermissionController
    public static final String UPSERT_PERMISSION_SUMMARY = "Create or update permission";
    public static final String GET_ALL_PERMISSIONS_SUMMARY = "Get all permissions";
    public static final String GET_GROUPED_PERMISSIONS_SUMMARY = "Get all permissions grouped by module";
    public static final String GET_GROUPED_PERMISSIONS_DESCRIPTION = "Returns permissions nested under each module. Use this for the permission management UI — renders a module card with checkboxes for each action.";
    public static final String GET_ACTIVE_PERMISSIONS_SUMMARY = "Get active permissions";
    public static final String GET_PERMISSION_BY_ID_SUMMARY = "Get permission by ID";
    public static final String DELETE_PERMISSION_SUMMARY = "Delete permission";
    public static final String TOGGLE_PERMISSION_SUMMARY = "Toggle permission status";

    // RoleController
    public static final String UPSERT_ROLE_SUMMARY = "Create or update role";
    public static final String GET_ALL_ROLES_SUMMARY = "Get all roles";
    public static final String GET_ACTIVE_ROLES_SUMMARY = "Get active roles";
    public static final String GET_ROLE_BY_ID_SUMMARY = "Get role by ID";
    public static final String DELETE_ROLE_SUMMARY = "Delete role";
    public static final String TOGGLE_ROLE_SUMMARY = "Toggle role status";
    public static final String ASSIGN_PERMISSIONS_TO_ROLE_SUMMARY = "Assign permissions to a role";
    public static final String GET_ROLE_PERMISSIONS_SUMMARY = "Get permissions for a role";

    // RoleController — template management (delegation chain)
    public static final String GET_TEMPLATES_SUMMARY = "List system role templates with permissions";
    public static final String GET_TEMPLATES_DESCRIPTION = "Returns all system role templates (FIRM_ADMIN, ADVOCATE, PARALEGAL, CLIENT, SUPER_ADMIN) with their current permission sets. These templates are the ceiling that firm-scoped role clones inherit at onboarding. Editable by Super Admin via the template permissions endpoints.";

    public static final String GET_TEMPLATE_PERMISSIONS_SUMMARY = "Get one template's permissions";

    public static final String PREVIEW_TEMPLATE_CHANGE_SUMMARY = "Dry-run a template permission change";
    public static final String PREVIEW_TEMPLATE_CHANGE_DESCRIPTION = "Computes exactly what a template edit would do across all firms — delta, per-firm impact, ceiling skips, and the narrowing cascade — without writing anything. Call this before PUT; the cascade is never silent.";

    public static final String UPDATE_TEMPLATE_PERMISSIONS_SUMMARY = "Edit a system role template's permissions (Super Admin)";
    public static final String UPDATE_TEMPLATE_PERMISSIONS_DESCRIPTION = "Validates the delegation chain, persists the template delta, freezes the template against boot re-seeding, and enqueues an async sync job that propagates the change to every firm clone. SUPER_ADMIN template is immutable. Chain violations are rejected with the offending permission codes named.";

    public static final String GET_SYNC_JOB_STATUS_SUMMARY = "Get template sync job status";
    public static final String GET_SYNC_JOB_STATUS_DESCRIPTION = "Poll a template sync job: firms completed/total, per-firm failures, and timestamps. Every audit row written by the job carries the job id, so per-firm changes trace back to the SA edit that caused them.";
}
