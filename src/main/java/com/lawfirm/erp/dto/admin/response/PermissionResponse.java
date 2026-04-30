package com.lawfirm.erp.dto.admin.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class PermissionResponse {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
