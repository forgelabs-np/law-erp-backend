package com.lawfirm.erp.dto.admin.response;

import com.lawfirm.erp.common.enums.PermissionAction;
import com.lawfirm.erp.common.enums.PermissionScope;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PermissionResponse {
    private UUID id;
    private PermissionAction action;
    private PermissionScope scope;
    private String code;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
}