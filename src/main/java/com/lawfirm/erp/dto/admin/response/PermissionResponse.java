package com.lawfirm.erp.dto.admin.response;

import com.lawfirm.erp.common.enums.ModuleCode;
import com.lawfirm.erp.common.enums.PermissionAction;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PermissionResponse {
    private UUID id;
    private ModuleCode module;
    private PermissionAction action;
    private String code;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
}