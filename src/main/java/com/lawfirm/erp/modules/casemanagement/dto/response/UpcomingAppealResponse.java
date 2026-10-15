package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class UpcomingAppealResponse {

    private UUID id;

    private String ourCourtCaseRef;

    private CourtLevel courtLevel;

    private String courtName;

    private LocalDate judgmentDate;

    private LocalDate appealDeadline;

    private boolean partyIsState;

    private boolean appealRequiresLeave;

    private String matterNumber;

    private String matterTitle;
}
