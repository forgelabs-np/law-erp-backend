package com.lawfirm.erp.dto.firm.response;

import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.enums.FirmType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class FirmProfileResponse {
    private UUID id;
    private String lawFirmCode;
    private String name;
    private FirmType firmType;
    private FirmStatus status;
    private String email;
    private String phone;
    private String address;
    private String jurisdiction;
    private String logoUrl;
    /** Trial badge/expiry for the firm's own profile screen — mirrors the Super Admin list. */
    private Boolean isTrial;
    private Integer trialDays;
    private LocalDateTime trialExpiresAt;
    private LocalDateTime createdAt;
}