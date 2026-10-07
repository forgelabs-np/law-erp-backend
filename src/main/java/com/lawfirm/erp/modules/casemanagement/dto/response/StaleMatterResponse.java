package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class StaleMatterResponse {

    private UUID id;

    private String matterNumber;

    private String title;

    private MatterType matterType;

    private MatterStatus status;

    private String currentCourtCaseRef;

    private Integer daysSinceLastPeshi;
}
