package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStage;
import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What a client sees about their own case in the portal — deliberately narrow:
 * no internal notes, no assignments, no audit trail, no other party's contact details.
 */
@Data
@Builder
public class ClientMatterResponse {

    private UUID id;
    private String matterNumber;
    private String title;
    private MatterType matterType;
    private MatterStatus status;
    private CourtLevel originatingCourtLevel;

    // ── Current proceeding (the leaf the firm is working on) ─────────────────
    private String currentCourtCaseRef;
    private String courtName;
    private CourtCaseStage stage;
    private LocalDateTime nextHearingAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
