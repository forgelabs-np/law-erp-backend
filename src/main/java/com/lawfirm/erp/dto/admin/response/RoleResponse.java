package com.lawfirm.erp.dto.admin.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RoleResponse {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Boolean isSystem;
    private Boolean isActive;
    private List<FeaturePermissionResponse> permissions;
    private LocalDateTime createdAt;
}