package com.lawfirm.erp.dto.firm.response;

import com.lawfirm.erp.common.enums.UserType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ClientResponse {
    private UUID id;
    private String username;
    private String email;
    private String mobileNo;
    private String fullName;
    private UserType userType;
    private Boolean isActive;
    private Boolean portalAccessEnabled;
    private LocalDateTime createdAt;
}