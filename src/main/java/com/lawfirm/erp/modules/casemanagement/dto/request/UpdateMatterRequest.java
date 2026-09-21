package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateMatterRequest {

    private String title;

    private String description;    private UUID assignedPartnerId;
    private MatterStatus status;

    /** Re-assign this matter to a different client (must belong to the same firm). */
    private UUID clientUserId;

    /** Optional display-name override; defaults to the client's full name. */
    private String clientName;
}
