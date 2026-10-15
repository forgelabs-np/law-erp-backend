package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStage;
import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ClientMatterResponse {

    private UUID id;
    private String matterNumber;
    private String title;
    private MatterType matterType;
    private MatterStatus status;
    private CourtLevel originatingCourtLevel;

    private String currentCourtCaseRef;
    private String courtName;
    private CourtCaseStage stage;
    private LocalDateTime nextHearingAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
