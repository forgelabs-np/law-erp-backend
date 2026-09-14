package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateMatterRequest {

    private String title;

    private String description;

    private UUID assignedPartnerId;

    private MatterStatus status;
}
