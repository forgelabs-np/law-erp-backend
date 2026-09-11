package com.lawfirm.erp.dto.Tenant.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TenantTypeResponse {
    private UUID id;
    private String name;
    private String code;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
}