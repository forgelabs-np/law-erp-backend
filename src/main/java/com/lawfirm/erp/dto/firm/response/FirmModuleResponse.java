package com.lawfirm.erp.dto.firm.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class FirmModuleResponse {
    private UUID id;
    private UUID moduleId;
    private String moduleName;
    private String moduleCode;
    private Boolean isEnabled;
    private LocalDateTime enabledAt;
    private LocalDateTime expiresAt;
    private Boolean isTrial;
}