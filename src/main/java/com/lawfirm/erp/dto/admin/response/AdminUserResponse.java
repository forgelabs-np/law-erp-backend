package com.lawfirm.erp.dto.admin.response;

import com.lawfirm.erp.common.enums.UserType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

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

    private UUID roleId;
    private String roleName;
    private String roleCode;

    private UUID firmId;
    private String firmCode;
    private String firmName;
}
