package com.lawfirm.erp.modules.usermanagement.dto.response;

import com.lawfirm.erp.common.enums.UserType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full user profile — returned by GET /modules/users/{id}/profile
 *
 * Contains:
 *  - Basic info
 *  - Role details
 *  - All permissions this user has (flat list + grouped by module)
 *  - Last 10 audit entries
 *  - Action count this month
 */
@Data
@Builder
public class UserProfileResponse {

    // ── Basic info ─────────────────────────────────────────────────────────
    private UUID id;
    private String username;
    private String fullName;
    private String email;
    private String mobileNo;
    private String profilePhotoUrl;
    private UserType userType;
    private boolean isActive;
    private Boolean isBlocked;
    private Boolean portalAccessEnabled;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Role ───────────────────────────────────────────────────────────────
    private UUID roleId;
    private String roleName;
    private String roleCode;

    // ── Permissions ────────────────────────────────────────────────────────
    // Flat list — e.g. ["CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE"]
    private List<String> permissions;

    // Grouped by module — for the UI "what can this user do?" screen
    private List<ModulePermissionGroup> permissionsByModule;

    // ── Recent activity ────────────────────────────────────────────────────
    private List<ActivityEntry> recentActivity;   // last 10
    private long actionsThisMonth;

    // ── Nested types ───────────────────────────────────────────────────────

    @Data
    @Builder
    public static class ModulePermissionGroup {
        private String moduleCode;
        private String moduleName;          // "Case Management"
        private List<String> actions;       // ["VIEW", "CREATE", "EDIT"]
    }

    @Data
    @Builder
    public static class ActivityEntry {
        private String action;              // "CASE_CREATED"
        private String entityType;          // "CASE"
        private UUID entityId;
        private String summary;             // "Created case: Smith v Jones"
        private String ipAddress;
        private LocalDateTime createdAt;
    }
}
