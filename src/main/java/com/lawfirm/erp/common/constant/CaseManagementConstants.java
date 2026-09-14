package com.lawfirm.erp.common.constant;

public final class CaseManagementConstants {

    private CaseManagementConstants() {}

    // MatterController
    public static final String CREATE_MATTER_SUMMARY = "Create a matter";
    public static final String CREATE_MATTER_DESCRIPTION = "Creates the Matter + ORIGINAL CourtCase at the originating court level, with optional parties";

    public static final String LIST_MATTERS_SUMMARY = "List matters";
    public static final String LIST_MATTERS_DESCRIPTION = "Paginated list with filters (matterType, status, search)";

    public static final String GET_STALE_MATTERS_SUMMARY = "Stale matters";
    public static final String GET_STALE_MATTERS_DESCRIPTION = "Matters whose current leaf hasn't had a real Peshi in N days (long-pending Tarik chains)";

    public static final String GET_MATTER_SUMMARY = "Get matter details";
    public static final String GET_MATTER_DESCRIPTION = "Matter with full CourtCase chain, roles and parties. Use matterNumber (e.g. APX-MAT-2026-00001).";

    public static final String UPDATE_MATTER_SUMMARY = "Update matter details";

    public static final String GET_TIMELINE_SUMMARY = "Matter timeline";
    public static final String GET_TIMELINE_DESCRIPTION = "Aggregated event history across all CourtCases, each tagged with its court case";

    public static final String GET_FIRM_TIMELINE_SUMMARY = "Overall timeline";
    public static final String GET_FIRM_TIMELINE_DESCRIPTION = "Firm-wide activity feed across all matters, newest first — filters: matterType, status, from, to (YYYY-MM-DD)";

    public static final String ADD_COURT_CASE_SUMMARY = "Add court case";
    public static final String ADD_COURT_CASE_DESCRIPTION = "Attach an appeal / remand / writ / review CourtCase to the matter chain";

    public static final String ADD_PARTY_SUMMARY = "Add party to matter";

    public static final String MATCH_PARTY_SUMMARY = "Match a party against existing clients and matter parties";

    // CourtEventController
    public static final String SCHEDULE_EVENT_SUMMARY = "Schedule a Tarik/Peshi event";
    public static final String SCHEDULE_EVENT_DESCRIPTION = "Conflict detection against all of the advocate's events firm-wide";

    public static final String LIST_EVENTS_SUMMARY = "List events for a court case";
    public static final String LIST_EVENTS_DESCRIPTION = "Chained Tarik/Peshi stream, ordered by sequence";

    public static final String GET_EVENT_SUMMARY = "Get event details";

    public static final String UPDATE_EVENT_SUMMARY = "Update event details";

    public static final String MARK_HELD_SUMMARY = "Mark event held";
    public static final String MARK_HELD_DESCRIPTION = "Records outcome and creates the next Tarik/Peshi event the court gave";

    public static final String CANCEL_EVENT_SUMMARY = "Cancel an event";

    // DashboardController
    public static final String GET_DASHBOARD_SUMMARY = "Get dashboard";
    public static final String GET_DASHBOARD_DESCRIPTION = "Firm-wide stats for FIRM_ADMIN; assigned-matters-only for ADVOCATE/PARALEGAL. Includes total/active/stale counts, today's events, and case positioning summaries.";
}
