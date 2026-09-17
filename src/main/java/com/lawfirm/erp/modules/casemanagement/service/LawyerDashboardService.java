package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.modules.casemanagement.dto.response.LawyerDashboardResponse;

import java.util.UUID;

/**
 * Service interface for lawyer dashboard functionality.
 * Combines case management and scraper data to make lawyers' lives easier.
 */
public interface LawyerDashboardService {

    /**
     * Get comprehensive dashboard for a lawyer.
     * Includes stats, critical cases, upcoming hearings, deadlines, and recent updates.
     */
    LawyerDashboardResponse getLawyerDashboard(UUID advocateId);

    /**
     * Get all active cases assigned to a specific lawyer with current status.
     */
    LawyerDashboardResponse.CaseSummary getLawyerCases(UUID advocateId);

    /**
     * Get upcoming hearings for a specific lawyer from both scraper and internal court events.
     */
    LawyerDashboardResponse.HearingSummary getLawyerHearings(UUID advocateId, int withinDays);

    /**
     * Get upcoming deadlines for a specific lawyer.
     */
    LawyerDashboardResponse.DeadlineSummary getLawyerDeadlines(UUID advocateId, int withinDays);
}
