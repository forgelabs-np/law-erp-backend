package com.lawfirm.erp.dto.firm.request;

import lombok.Data;

import java.util.UUID;

@Data
public class EnableModuleRequest {
    private UUID moduleId;
    private Boolean isEnabled = true;
}