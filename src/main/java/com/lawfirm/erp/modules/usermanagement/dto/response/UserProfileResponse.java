package com.lawfirm.erp.modules.usermanagement.dto.response;

import com.lawfirm.erp.common.enums.UserType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserProfileResponse {

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

    private UUID roleId;
    private String roleName;
    private String roleCode;

    private List<String> permissions;

    private List<ModulePermissionGroup> permissionsByModule;

    private List<ActivityEntry> recentActivity;
    private long actionsThisMonth;


    @Data
    @Builder
    public static class ModulePermissionGroup {
        private String moduleCode;
        private String moduleName;
        private List<String> actions;
    }

    @Data
    @Builder
    public static class ActivityEntry {
        private String action;
        private String entityType;
        private UUID entityId;
        private String summary;
        private String ipAddress;
        private LocalDateTime createdAt;
    }
}
