package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.modules.casemanagement.dto.response.LawyerDashboardResponse;

import java.util.UUID;

public interface LawyerDashboardService {

    LawyerDashboardResponse getLawyerDashboard(UUID advocateId);

    LawyerDashboardResponse.CaseSummary getLawyerCases(UUID advocateId);

    LawyerDashboardResponse.HearingSummary getLawyerHearings(UUID advocateId, int withinDays);

    LawyerDashboardResponse.DeadlineSummary getLawyerDeadlines(UUID advocateId, int withinDays);
}
