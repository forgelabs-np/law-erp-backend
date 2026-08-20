package com.lawfirm.erp.common.constant;

public final class CaseManagementConstants {

    private CaseManagementConstants() {}

    // MatterController
    public static final String CREATE_MATTER_SUMMARY = "Create a new matter";
    public static final String LIST_MATTERS_SUMMARY = "List all matters";
    public static final String GET_MATTER_SUMMARY = "Get matter by number";
    public static final String UPDATE_MATTER_SUMMARY = "Update matter details";
    public static final String ADD_COURT_CASE_SUMMARY = "Add a court case to a matter";
    public static final String ADD_PARTY_SUMMARY = "Add a party to a matter";
    public static final String GET_TIMELINE_SUMMARY = "Get matter timeline";
    public static final String GET_FIRM_TIMELINE_SUMMARY = "Get firm-wide timeline";
    public static final String GET_STALE_MATTERS_SUMMARY = "Get stale matters";

    // CourtCaseController
    public static final String GET_COURT_CASE_SUMMARY = "Get court case by ref";
    public static final String UPDATE_COURT_CASE_SUMMARY = "Update court case";
    public static final String UPDATE_STAGE_SUMMARY = "Update court case stage";
    public static final String RECORD_JUDGMENT_SUMMARY = "Record judgment";
    public static final String GET_ALLOWED_STAGES_SUMMARY = "Get allowed stage transitions";
    public static final String GET_UPCOMING_APPEALS_SUMMARY = "Get upcoming appeal deadlines";

    // CourtEventController
    public static final String SCHEDULE_EVENT_SUMMARY = "Schedule a court event";
    public static final String LIST_EVENTS_SUMMARY = "List events for a court case";
    public static final String GET_EVENT_SUMMARY = "Get event by ID";
    public static final String UPDATE_EVENT_SUMMARY = "Update a court event";
    public static final String MARK_HELD_SUMMARY = "Mark event as held";
    public static final String CANCEL_EVENT_SUMMARY = "Cancel a court event";

    // CalendarController
    public static final String GET_EVENTS_SUMMARY = "Get calendar events";

    // DashboardController
    public static final String GET_DASHBOARD_SUMMARY = "Get case management dashboard";

    // CaseAssignmentController
    public static final String ASSIGN_SUMMARY = "Assign employee to matter";
    public static final String REVOKE_SUMMARY = "Revoke assignment from matter";
    public static final String LIST_ASSIGNMENTS_SUMMARY = "List assignments for matter";
}
