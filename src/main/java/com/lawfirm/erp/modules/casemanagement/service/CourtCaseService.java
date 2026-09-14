package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.modules.casemanagement.dto.request.RecordJudgmentRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateCourtCaseRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateCourtCaseStageRequest;
import com.lawfirm.erp.modules.casemanagement.dto.response.CourtCaseResponse;
import com.lawfirm.erp.modules.casemanagement.dto.response.UpcomingAppealResponse;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStage;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CourtCaseService {

    CourtCaseResponse getCourtCase(String ourCourtCaseRef);

    CourtCaseResponse updateCourtCase(String ourCourtCaseRef, UpdateCourtCaseRequest request);

    CourtCaseResponse updateStage(String ourCourtCaseRef, UpdateCourtCaseStageRequest request);

    CourtCaseResponse recordJudgment(String ourCourtCaseRef, RecordJudgmentRequest request);

    void recordJudgmentInternal(CourtCase cc, Matter matter, LocalDate judgmentDate,
                                String judgmentSummary, UUID decisionInFavorOfPartyId);

    List<CourtCaseStage> getAllowedStages(String ourCourtCaseRef);

    List<UpcomingAppealResponse> listUpcomingAppealDeadlines(int withinDays);
}
