package com.lawfirm.erp.modules.casemanagement.controller;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.casemanagement.dto.request.AddCourtCaseRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.AssignCaseRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.CreateMatterRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.MarkCourtEventHeldRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.PartyEntryRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.PartyMatchRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.RecordJudgmentRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.ScheduleCourtEventRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateCourtCaseRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateCourtCaseStageRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateCourtEventRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateMatterRequest;
import com.lawfirm.erp.modules.casemanagement.enums.AssignmentRole;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStage;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventType;
import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import com.lawfirm.erp.modules.casemanagement.enums.NextEventType;
import com.lawfirm.erp.modules.casemanagement.enums.OutcomeType;
import com.lawfirm.erp.modules.casemanagement.enums.PartyType;
import com.lawfirm.erp.modules.casemanagement.service.CaseAssignmentService;
import com.lawfirm.erp.modules.casemanagement.service.CourtCaseService;
import com.lawfirm.erp.modules.casemanagement.service.CourtEventService;
import com.lawfirm.erp.modules.casemanagement.service.MatterService;
import com.lawfirm.erp.modules.casemanagement.service.PartyMatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Locks the permission enforcement on the Case Management module —
 * READ_ONLY roles (PARALEGAL) must not be able to create matters/events,
 * and every write must gate on CASE_MANAGEMENT:*.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CaseManagementAuthzTest {

    private static final String MATTER_NO = "APX-MAT-2026-00001";
    private static final String REF = "APX-MAT-2026-00001-DC1";

    @Mock private MatterService matterService;
    @Mock private PartyMatchService partyMatchService;
    @Mock private CourtEventService courtEventService;
    @Mock private CaseAssignmentService assignmentService;
    @Mock private CourtCaseService courtCaseService;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private PermissionEvaluator permissionEvaluator;
    @Mock private ResponseHandler responseHandler;

    @InjectMocks private MatterController matterController;
    @InjectMocks private CourtEventController courtEventController;
    @InjectMocks private CaseAssignmentController caseAssignmentController;
    @InjectMocks private CourtCaseController courtCaseController;

    private <T> ApiRequest<T> wrap(T data) {
        ApiRequest<T> req = new ApiRequest<>();
        req.setData(data);
        return req;
    }

    // ── MatterController ────────────────────────────────────────────────────

    private CreateMatterRequest matterRequest() {
        CreateMatterRequest req = new CreateMatterRequest();
        req.setMatterType(MatterType.CIVIL);
        req.setTitle("Test Matter");
        req.setOriginatingCourtLevel(CourtLevel.DISTRICT);
        req.setCourtName("District Court");
        return req;
    }

    @Test
    @DisplayName("Create matter requires CASE_MANAGEMENT:CREATE (blocks READ_ONLY paralegal)")
    void createMatterRequiresCreate() {
        matterController.createMatter(wrap(matterRequest()));
        verify(permissionEvaluator).require("CASE_MANAGEMENT:CREATE");
    }

    @Test
    @DisplayName("Create matter without permission is forbidden and never reaches service")
    void createMatterDenied() {
        doThrow(new ForbiddenException("Missing required permission: CASE_MANAGEMENT:CREATE"))
                .when(permissionEvaluator).require(anyString());

        assertThrows(ForbiddenException.class,
                () -> matterController.createMatter(wrap(matterRequest())));
        verify(matterService, never()).createMatter(any());
    }

    @Test
    @DisplayName("Matter read/update endpoints require VIEW/EDIT/CREATE respectively")
    void matterEndpointsRequirePermissions() {
        org.mockito.Mockito.when(currentUserResolver.getCurrentFirmId())
                .thenReturn(UUID.randomUUID());
        matterController.listMatters(null, null, null, 0, 20);
        matterController.getMatter(MATTER_NO);
        matterController.updateMatter(MATTER_NO, wrap(new UpdateMatterRequest()));
        matterController.addCourtCase(MATTER_NO, wrap(new AddCourtCaseRequest()));
        matterController.addParty(MATTER_NO, wrap(partyRequest()));
        matterController.matchParty(wrap(new PartyMatchRequest()));

        verify(permissionEvaluator, org.mockito.Mockito.times(3)).require("CASE_MANAGEMENT:VIEW");
        verify(permissionEvaluator).require("CASE_MANAGEMENT:EDIT");
        verify(permissionEvaluator, org.mockito.Mockito.times(2)).require("CASE_MANAGEMENT:CREATE");
    }

    private PartyEntryRequest partyRequest() {
        PartyEntryRequest req = new PartyEntryRequest();
        req.setFullName("Party");
        req.setRoleType(PartyType.PLAINTIFF);
        return req;
    }

    // ── CourtEventController ────────────────────────────────────────────────

    @Test
    @DisplayName("Court event endpoints require CREATE/VIEW/EDIT/DELETE")
    void courtEventEndpointsRequirePermissions() {
        ScheduleCourtEventRequest schedule = new ScheduleCourtEventRequest();
        schedule.setEventType(CourtEventType.TARIK);
        schedule.setScheduledDate(LocalDate.now());
        courtEventController.scheduleEvent(REF, wrap(schedule));
        courtEventController.listEvents(REF);
        courtEventController.getEvent(UUID.randomUUID());
        courtEventController.updateEvent(UUID.randomUUID(), wrap(new UpdateCourtEventRequest()));

        MarkCourtEventHeldRequest held = new MarkCourtEventHeldRequest();
        held.setOutcomeType(OutcomeType.ADJOURNED_NO_PROGRESS);
        held.setNextEventType(NextEventType.TARIK);
        courtEventController.markHeld(UUID.randomUUID(), wrap(held));
        courtEventController.cancelEvent(UUID.randomUUID());

        verify(permissionEvaluator).require("CASE_MANAGEMENT:CREATE");
        verify(permissionEvaluator, org.mockito.Mockito.times(2)).require("CASE_MANAGEMENT:VIEW");
        verify(permissionEvaluator, org.mockito.Mockito.times(2)).require("CASE_MANAGEMENT:EDIT");
        verify(permissionEvaluator).require("CASE_MANAGEMENT:DELETE");
    }

    @Test
    @DisplayName("Scheduling an event without permission is forbidden")
    void scheduleEventDenied() {
        doThrow(new ForbiddenException("Missing required permission: CASE_MANAGEMENT:CREATE"))
                .when(permissionEvaluator).require(anyString());

        ScheduleCourtEventRequest schedule = new ScheduleCourtEventRequest();
        schedule.setEventType(CourtEventType.TARIK);
        schedule.setScheduledDate(LocalDate.now());

        assertThrows(ForbiddenException.class,
                () -> courtEventController.scheduleEvent(REF, wrap(schedule)));
        verify(courtEventService, never()).scheduleEvent(anyString(), any());
    }

    // ── CaseAssignmentController ────────────────────────────────────────────

    @Test
    @DisplayName("Case assignment endpoints require CREATE/DELETE/VIEW")
    void assignmentEndpointsRequirePermissions() {
        AssignCaseRequest assign = new AssignCaseRequest();
        assign.setUserId(UUID.randomUUID());
        assign.setAssignmentRole(AssignmentRole.PRIMARY_ADVOCATE);
        caseAssignmentController.assign(MATTER_NO, wrap(assign));
        caseAssignmentController.revoke(MATTER_NO, UUID.randomUUID());
        caseAssignmentController.listByMatter(MATTER_NO);

        verify(permissionEvaluator).require("CASE_MANAGEMENT:CREATE");
        verify(permissionEvaluator).require("CASE_MANAGEMENT:DELETE");
        verify(permissionEvaluator).require("CASE_MANAGEMENT:VIEW");
    }

    // ── CourtCaseController ─────────────────────────────────────────────────

    @Test
    @DisplayName("Court case endpoints require VIEW for reads and EDIT for writes")
    void courtCaseEndpointsRequirePermissions() {
        courtCaseController.getCourtCase(REF);
        courtCaseController.updateCourtCase(REF, wrap(new UpdateCourtCaseRequest()));

        UpdateCourtCaseStageRequest stage = new UpdateCourtCaseStageRequest();
        stage.setStage(CourtCaseStage.SUMMONS_ISSUED);
        courtCaseController.updateStage(REF, wrap(stage));

        courtCaseController.recordJudgment(REF, wrap(new RecordJudgmentRequest()));
        courtCaseController.upcomingAppealDeadlines(30);
        courtCaseController.allowedStages(REF);

        verify(permissionEvaluator, org.mockito.Mockito.times(3)).require("CASE_MANAGEMENT:VIEW");
        verify(permissionEvaluator, org.mockito.Mockito.times(3)).require("CASE_MANAGEMENT:EDIT");
    }

    @Test
    @DisplayName("Stage update without permission is forbidden")
    void updateStageDenied() {
        doThrow(new ForbiddenException("Missing required permission: CASE_MANAGEMENT:EDIT"))
                .when(permissionEvaluator).require(anyString());

        UpdateCourtCaseStageRequest stage = new UpdateCourtCaseStageRequest();
        stage.setStage(CourtCaseStage.SUMMONS_ISSUED);

        assertThrows(ForbiddenException.class,
                () -> courtCaseController.updateStage(REF, wrap(stage)));
        verify(courtCaseService, never()).updateStage(anyString(), any());
    }
}
