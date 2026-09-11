package com.lawfirm.erp.dto.firm.response;

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
    private UUID firmId;
    private String firmName;
    private String firmCode;
    private Boolean isActive;
    private LocalDateTime createdAt;
}