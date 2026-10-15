package com.lawfirm.erp.modules.usermanagement.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserPermissionsResponse {
    private UUID userId;
    private String username;
    private String roleCode;
    private String roleName;

    private List<String> allPermissions;

    private List<UserProfileResponse.ModulePermissionGroup> byModule;
}
