package com.lawfirm.erp.modules.usermanagement.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Returned by GET /modules/users/{id}/permissions
 * Answers: "exactly what can this user do, and in which modules?"
 */
@Data
@Builder
public class UserPermissionsResponse {
    private UUID userId;
    private String username;
    private String roleCode;
    private String roleName;

    // Full flat list — e.g. ["CASE_MANAGEMENT:VIEW", "BILLING:VIEW"]
    private List<String> allPermissions;

    // Grouped — for the UI permissions breakdown screen
    private List<UserProfileResponse.ModulePermissionGroup> byModule;
}
