package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Dashboard flag: matters whose current leaf hasn't had a real Peshi in N days
 * (long-pending Tarik chains invisible in a flat hearing list).
 */
@Data
@Builder
public class StaleMatterResponse {

    private UUID id;

    private String matterNumber;

    private String title;

    private MatterType matterType;

    private MatterStatus status;

    private String currentCourtCaseRef;

    /** Null when the matter has never had a Peshi. */
    private Integer daysSinceLastPeshi;
}
