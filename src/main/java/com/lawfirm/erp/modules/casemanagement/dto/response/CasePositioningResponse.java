package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStage;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventType;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class CasePositioningResponse {

    private UUID matterId;
    private String matterNumber;
    private String matterTitle;
    private MatterType matterType;
    private MatterStatus matterStatus;

    // Current court case (leaf)
    private UUID courtCaseId;
    private String ourCourtCaseRef;
    private String courtName;
    private CourtCaseStage stage;
    private CourtCaseStatus caseStatus;
    private UUID advocateId;

    // Last hearing
    private LocalDate lastHearingDate;
    private CourtEventType lastHearingType;
    private CourtEventStatus lastHearingStatus;
    private String lastHearingOutcome;

    // Next event
    private LocalDate nextEventDate;
    private CourtEventType nextEventType;
    private String nextEventCourtRoom;
    private String nextEventJudge;

    // Stats
    private int totalEvents;
    private Integer daysSinceLastHearing;
    private boolean stale;
}
