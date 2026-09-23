package com.lawfirm.erp.dto.firm.response;

import com.lawfirm.erp.common.enums.FirmStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class FirmAdminResponse {
    private UUID id;
    private String username;
    private String email;
    private String mobileNo;
    private String fullName;
    private UUID roleId;
    private String roleName;
    private UUID firmId;
    private String firmName;
    private String firmCode;
    private String firmEmail;
    private FirmStatus firmStatus;
    private String firmType;
    private String firmAddress;
    private String firmPhone;
    /** The firm's own trial state — the Super Admin console lists firms through this response. */
    private Boolean isTrial;
    private Integer trialDays;
    private LocalDateTime trialExpiresAt;
    private Boolean isActive;
    private LocalDateTime createdAt;
}