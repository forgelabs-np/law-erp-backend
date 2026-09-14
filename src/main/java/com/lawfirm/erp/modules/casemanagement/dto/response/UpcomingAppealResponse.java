package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the appeal-deadline watch: a decided case whose statutory appeal
 * window closes soon and no appeal has been filed yet.
 */
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

    /** True for High Court judgments — the further appeal needs a leave petition first. */
    private boolean appealRequiresLeave;

    private String matterNumber;

    private String matterTitle;
}
