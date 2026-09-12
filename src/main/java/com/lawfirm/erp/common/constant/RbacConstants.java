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
}
