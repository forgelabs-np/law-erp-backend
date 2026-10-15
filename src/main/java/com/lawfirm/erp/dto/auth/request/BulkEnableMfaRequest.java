package com.lawfirm.erp.dto.auth.request;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BulkEnableMfaRequest {

    private List<UUID> userIds;

    private UUID firmId;

    private Boolean allFirmAdmins;
}
