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

    // What this role currently has assigned
    private List<PermissionResponse> currentPermissions;

    // Full ceiling — everything this role TYPE is allowed to have
    // assigned = true means it's currently active on this role
    private List<AvailablePermission> availablePermissions;

    @Data
    @Builder
    public static class AvailablePermission {
        private UUID id;
        private String code;        // "CASE_MANAGEMENT:VIEW"
        private String action;      // "VIEW"
        private String moduleCode;  // "CASE_MANAGEMENT"
        private String description;
        private boolean assigned;   // true = currently on this role
    }
}