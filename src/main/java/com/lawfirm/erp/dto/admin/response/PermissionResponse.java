package com.lawfirm.erp.dto.admin.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PermissionResponse {
    private UUID id;
    private String code;
    private String module;
    private String action;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
}