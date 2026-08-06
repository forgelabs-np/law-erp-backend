package com.lawfirm.erp.modules.casemanagement.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class LinkPartyRequest {

    @NotNull(message = "Client ID is required")
    private UUID clientId;

    private boolean isOurClient;
}
