package com.lawfirm.erp.modules.me.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@Data
@Builder
public class MeResponse {

    private UUID id;
    private String username;
    private String fullName;
    private String email;
    private String mobileNo;
    private String profilePhotoUrl;
    private String userType;

    private FirmInfo firm;

    private RoleInfo role;

    private List<String> permissions;

    private List<ModuleAccess> modules;

    private String brandColorPrimary;
    private String brandColorSecondary;
    private String appName;
    private boolean logoAllowed;
    private String logoUrl;

    private boolean isActive;
    private LocalDateTime lastLoginAt;


    @Data
    @Builder
    public static class FirmInfo {
        private UUID id;
        private String name;
        private String lawFirmCode;
        private String email;
        private String phone;
        private String address;
        private String jurisdiction;
        private String logoUrl;
        private boolean logoAllowed;
        private String brandPrimaryHex;
        private String brandSecondaryHex;

        /**
         * Present (and true) only when the firm has chosen its own brand colors. When the firm
         * has not set either hex, this is omitted so the portal falls back to the app default.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Boolean isPersonalColor;

        private String status;
        private boolean isTrial;
        private LocalDateTime trialExpiresAt;
        private Long daysRemaining;
    }

    @Data
    @Builder
    public static class RoleInfo {
        private UUID id;
        private String name;
        private String code;
        private boolean isSystem;
    }

    @Data
    @Builder
    public static class ModuleAccess {
        private String moduleCode;
        private String moduleName;
        private String icon;
        private String path;
        private boolean enabled;
        private List<String> actions;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<ModuleAccess> subModules;
    }
}