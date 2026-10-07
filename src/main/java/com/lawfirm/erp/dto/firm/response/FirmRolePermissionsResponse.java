package com.lawfirm.erp.dto.firm.response;

import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class FirmRolePermissionsResponse {

    private UUID roleId;
    private String roleName;
    private String roleCode;

    private List<PermissionResponse> currentPermissions;

    private List<AvailablePermission> availablePermissions;

    @Data
    @Builder
    public static class AvailablePermission {
        private UUID id;
        private String code;
        private String action;
        private String moduleCode;
        private String description;
        private boolean assigned;
    }
}