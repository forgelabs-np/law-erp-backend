package com.lawfirm.erp.dto.Tenant.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class TenantTypeResponse {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdDate;
}
