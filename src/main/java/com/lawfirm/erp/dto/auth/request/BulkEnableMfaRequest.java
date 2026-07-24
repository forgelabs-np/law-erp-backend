package com.lawfirm.erp.dto.auth.request;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BulkEnableMfaRequest {

    // Option 1: specific user IDs
    private List<UUID> userIds;

    // Option 2: all eligible users in a specific firm
    private UUID firmId;

    // Option 3: all FIRM_ADMINs across all firms
    private Boolean allFirmAdmins;
}
