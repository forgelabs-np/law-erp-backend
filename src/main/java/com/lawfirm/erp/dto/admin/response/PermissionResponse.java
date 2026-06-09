package com.lawfirm.erp.dto.admin.response;

import com.lawfirm.erp.common.enums.PermissionAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResponse {
    private UUID id;
    private ModuleResponse module;
    private PermissionAction action;
    private String code;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
}