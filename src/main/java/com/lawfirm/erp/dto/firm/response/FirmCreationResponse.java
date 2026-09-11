package com.lawfirm.erp.dto.firm.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class FirmCreationResponse {
    private UUID firmId;
    private String lawFirmCode;
    private String firmName;
    private UUID adminUserId;
    private String adminUsername;
    private String message;
}