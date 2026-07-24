package com.lawfirm.erp.modules.me.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@Data
@Builder
public class MeResponse {

    // ── Who ───────────────────────────────────────────────────────────────
    private UUID id;
    private String username;
    private String fullName;
    private String email;
    private String mobileNo;
    private String profilePhotoUrl;
    private String userType;          // SUPER_ADMIN | FIRM_USER | CLIENT

    // ── Firm context ──────────────────────────────────────────────────────
    private FirmInfo firm;            // null for SUPER_ADMIN

    // ── Role ──────────────────────────────────────────────────────────────
    private RoleInfo role;

    // ── Permissions — flat list ───────────────────────────────────────────
    // e.g. ["CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE", "BILLING:VIEW"]
    // Frontend checks: permissions.includes("CASE_MANAGEMENT:DELETE")
    private List<String> permissions;

    // ── Permissions grouped by module — for menu building ─────────────────
    // Frontend iterates this to build sidebar menu
    private List<ModuleAccess> modules;

    // ── Brand config (from SystemConfig) ──────────────────────────────────
    private String brandColorPrimary;
    private String brandColorSecondary;
    private String appName;

    // ── Status ────────────────────────────────────────────────────────────
    private boolean isActive;
    private LocalDateTime lastLoginAt;

    // ── Nested types ──────────────────────────────────────────────────────

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
    }

    @Data
    @Builder
    public static class RoleInfo {
        private UUID id;
        private String name;
        private String code;         // ADVOCATE | PARALEGAL | FIRM_ADMIN | CLIENT
        private boolean isSystem;
    }

    @Data
    @Builder
    public static class ModuleAccess {
        private String moduleCode;   // "CASE_MANAGEMENT"
        private String moduleName;   // "Case Management"
        private String icon;         // "FolderIcon" — for sidebar rendering
        private String path;         // "/cases" — frontend route
        private boolean enabled;     // is module enabled for this firm?
        private List<String> actions;// ["VIEW","CREATE","EDIT"] — what this user can do
    }
}