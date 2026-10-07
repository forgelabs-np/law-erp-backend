package com.lawfirm.erp.dto.admin.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

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
