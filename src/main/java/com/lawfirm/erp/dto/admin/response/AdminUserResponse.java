package com.lawfirm.erp.dto.admin.response;

import com.lawfirm.erp.common.enums.UserType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User-first row for the super admin "users &amp; roles" view.
 * Each row carries the user's own fields plus its role and firm (nullable),
 * so the frontend can render "User → Role" without extra lookups.
 */
@Data
@Builder
public class AdminUserResponse {

    private UUID id;
    private String username;
    private String fullName;
    private String email;
    private String mobileNo;
    private UserType userType;
    private boolean isActive;
    private Boolean isBlocked;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    // ── Role (nullable) ────────────────────────────────────────────────
    private UUID roleId;
    private String roleName;
    private String roleCode;

    // ── Firm (nullable — super admin users point at the SYSTEM firm) ───
    private UUID firmId;
    private String firmCode;
    private String firmName;
}
