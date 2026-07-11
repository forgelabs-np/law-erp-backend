package com.lawfirm.erp.modules.usermanagement.dto.response;

import com.lawfirm.erp.common.enums.UserType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight user row — used in list and search endpoints.
 * Covers both FIRM_USER (employees) and CLIENT in one response.
 */
@Data
@Builder
public class UserSummaryResponse {
    private UUID id;
    private String username;
    private String fullName;
    private String email;
    private String mobileNo;
    private UserType userType;        // FIRM_USER | CLIENT
    private boolean isActive;
    private UUID roleId;
    private String roleName;
    private String roleCode;
    private Boolean portalAccessEnabled; // only meaningful for CLIENT
    private LocalDateTime createdAt;
}
