package com.lawfirm.erp.dto.admin.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Permissions grouped by module.
 * Returned by GET /api/v1/admin/permissions/grouped
 *
 * Response shape:
 * {
 *   "modules": [
 *     {
 *       "moduleCode": "CASE_MANAGEMENT",
 *       "moduleName": "Case Management",
 *       "moduleDescription": "Manage legal cases",
 *       "icon": "FolderIcon",
 *       "path": "/cases",
 *       "displayOrder": 1,
 *       "permissions": [
 *         { "id": "...", "action": "ACCESS", "code": "CASE_MANAGEMENT:ACCESS", ... },
 *         { "id": "...", "action": "VIEW",   "code": "CASE_MANAGEMENT:VIEW",   ... }
 *       ]
 *     },
 *     ...
 *   ]
 * }
 */
@Data
@Builder
public class GroupedPermissionResponse {

    private List<ModulePermissions> modules;

    @Data
    @Builder
    public static class ModulePermissions {
        private String moduleCode;
        private String moduleName;
        private String moduleDescription;
        private String icon;
        private String path;
        private Integer displayOrder;
        private List<PermissionResponse> permissions;
    }
}
