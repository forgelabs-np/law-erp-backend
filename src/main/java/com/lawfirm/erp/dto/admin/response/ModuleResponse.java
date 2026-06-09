package com.lawfirm.erp.dto.admin.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleResponse {
    private UUID id;
    private String name;
    private String code;
    private String description;
    private Integer displayOrder;
    private String icon;
    private String path;
    private Boolean isSystem;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PermissionResponse> permissions;
}