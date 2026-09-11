package com.lawfirm.erp.dto.firm.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RoleUserResponse {
    private UUID id;
    private String fullName;
    private String username;
    private String email;
    private String mobileNo;
    private Boolean isActive;
}
