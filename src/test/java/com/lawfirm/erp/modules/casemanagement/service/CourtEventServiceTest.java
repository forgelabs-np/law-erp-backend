package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.casemanagement.dto.request.MarkCourtEventHeldRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.ScheduleCourtEventRequest;
import com.lawfirm.erp.modules.casemanagement.dto.response.CourtEventResponse;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.CourtEvent;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.enums.*;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtEventRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterTimelineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourtEventServiceTest {

    private static final UUID FIRM_ID = UUID.randomUUID();

    @Mock private CourtEventRepository courtEventRepository;
    @Mock private CourtCaseRepository courtCaseRepository;
    @Mock private MatterRepository matterRepository;
    @Mock private MatterTimelineRepository matterTimelineRepository;
    @Mock private AuditService auditService;
    @Mock private CourtCaseServiceImpl courtCaseService;

    @InjectMocks
    private CourtEventServiceImpl courtEventService;

    @BeforeEach
    void setUp() {
        FirmContextHolder.set(FIRM_ID, "APX");
    }

    @AfterEach
    void tearDown() {
        FirmContextHolder.clear();
    }

    private CourtEvent event(CourtEventStatus status) {
        CourtEvent e = new CourtEvent();
        e.setId(UUID.randomUUID());
        e.setFirmId(FIRM_ID);
        e.setCourtCaseId(UUID.randomUUID());
        e.setEventType(CourtEventType.PESHI);
        e.setSequenceNo(1);
        e.setStatus(status);
        return e;
    }

    private CourtCase courtCase(CourtCaseStage stage, CourtCaseStatus status) {
        CourtCase cc = new CourtCase();
        cc.setId(UUID.randomUUID());
        cc.setFirmId(FIRM_ID);
        cc.setMatterId(UUID.randomUUID());
        cc.setCourtLevel(CourtLevel.DISTRICT);
        cc.setRelationType(RelationType.ORIGINAL);
        cc.setOurCourtCaseRef("APX-MAT-2026-00001-DC1");
        cc.setStage(stage);
        cc.setStatus(status);
        return cc;
    }

    private Matter matter() {
        Matter m = new Matter();
        m.setId(UUID.randomUUID());
        m.setFirmId(FIRM_ID);
        m.setMatterNumber("APX-MAT-2026-00001");
        m.setTitle("Ram vs Shyam");
        m.setMatterType(MatterType.CIVIL);
        return m;
    }

    // ========================================================================
    // Judgment two-path sync — /held must never DECIDE a case without judgment fields
    // ========================================================================

    @Test
    @DisplayName("markHeld with JUDGMENT_DELIVERED but no inline judgment fields is rejected")
    void judgmentDeliveredWithoutInlineFieldsThrows() {
        CourtEvent ev = event(CourtEventStatus.SCHEDULED);
        CourtCase cc = courtCase(CourtCaseStage.JUDGMENT_AWAITED, CourtCaseStatus.JUDGMENT_AWAITED);
        Matter matter = matter();
        when(courtEventRepository.findByIdAndFirmId(ev.getId(), FIRM_ID)).thenReturn(Optional.of(ev));
        when(courtCaseRepository.findById(ev.getCourtCaseId())).thenReturn(Optional.of(cc));
        when(matterRepository.findById(cc.getMatterId())).thenReturn(Optional.of(matter));
        when(courtEventRepository.save(any(CourtEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        MarkCourtEventHeldRequest req = new MarkCourtEventHeldRequest();
        req.setOutcomeType(OutcomeType.JUDGMENT_DELIVERED);
        req.setNextEventType(NextEventType.NONE);

        assertThrows(BusinessRuleException.class, () -> courtEventService.markHeld(ev.getId(), req));
        verify(courtCaseService, never()).recordJudgmentInternal(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("markHeld with JUDGMENT_DELIVERED + inline fields records the judgment (deadline computed)")
    void judgmentDeliveredWithInlineFieldsRecordsJudgment() {
        CourtEvent ev = event(CourtEventStatus.SCHEDULED);
        CourtCase cc = courtCase(CourtCaseStage.JUDGMENT_AWAITED, CourtCaseStatus.JUDGMENT_AWAITED);
        Matter matter = matter();
        when(courtEventRepository.findByIdAndFirmId(ev.getId(), FIRM_ID)).thenReturn(Optional.of(ev));
        when(courtCaseRepository.findById(ev.getCourtCaseId())).thenReturn(Optional.of(cc));
        when(matterRepository.findById(cc.getMatterId())).thenReturn(Optional.of(matter));
        when(courtEventRepository.save(any(CourtEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        MarkCourtEventHeldRequest req = new MarkCourtEventHeldRequest();
        req.setOutcomeType(OutcomeType.JUDGMENT_DELIVERED);
        req.setNextEventType(NextEventType.NONE);
        req.setJudgmentDate(LocalDate.of(2026, 8, 5));
        req.setJudgmentSummary("Decree in favor of plaintiff");
        req.setDecisionInFavorOfPartyId(UUID.randomUUID());

        CourtEventResponse response = courtEventService.markHeld(ev.getId(), req);

        assertEquals(CourtEventStatus.HELD, response.getStatus());
        verify(courtCaseService).recordJudgmentInternal(eq(cc), eq(matter),
                eq(LocalDate.of(2026, 8, 5)), eq("Decree in favor of plaintiff"), any());
        // No next event row is created for a judgment outcome
        verify(courtEventRepository, never()).save(argThat(e -> e.getSequenceNo() == 2));
    }

    @Test
    @DisplayName("markHeld with JUDGMENT_DELIVERED at a non-judgment stage is rejected")
    void judgmentDeliveredAtWrongStageThrows() {
        CourtEvent ev = event(CourtEventStatus.SCHEDULED);
        CourtCase cc = courtCase(CourtCaseStage.HEARING_STAGE, CourtCaseStatus.ACTIVE);
        Matter matter = matter();
        when(courtEventRepository.findByIdAndFirmId(ev.getId(), FIRM_ID)).thenReturn(Optional.of(ev));
        when(courtCaseRepository.findById(ev.getCourtCaseId())).thenReturn(Optional.of(cc));
        when(matterRepository.findById(cc.getMatterId())).thenReturn(Optional.of(matter));
        when(courtEventRepository.save(any(CourtEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // The delegated judgment path enforces stage validity — make the mock behave
        // like the real recordJudgmentInternal (rejects non-judgment stages).
        doThrow(new BusinessRuleException("Judgment can only be recorded from JUDGMENT_AWAITED"))
                .when(courtCaseService).recordJudgmentInternal(any(), any(), any(), any(), any());

        MarkCourtEventHeldRequest req = new MarkCourtEventHeldRequest();
        req.setOutcomeType(OutcomeType.JUDGMENT_DELIVERED);
        req.setNextEventType(NextEventType.NONE);
        req.setJudgmentDate(LocalDate.of(2026, 8, 5));
        req.setJudgmentSummary("Decree");

        assertThrows(BusinessRuleException.class, () -> courtEventService.markHeld(ev.getId(), req));
    }

    @Test
    @DisplayName("markHeld with an adjournment never touches the judgment path")
    void adjournedDoesNotRecordJudgment() {
        CourtEvent ev = event(CourtEventStatus.SCHEDULED);
        CourtCase cc = courtCase(CourtCaseStage.HEARING_STAGE, CourtCaseStatus.ACTIVE);
        Matter matter = matter();
        when(courtEventRepository.findByIdAndFirmId(ev.getId(), FIRM_ID)).thenReturn(Optional.of(ev));
        when(courtCaseRepository.findById(ev.getCourtCaseId())).thenReturn(Optional.of(cc));
        when(matterRepository.findById(cc.getMatterId())).thenReturn(Optional.of(matter));
        when(courtEventRepository.maxSequenceNo(cc.getId())).thenReturn(1);
        when(courtEventRepository.save(any(CourtEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        MarkCourtEventHeldRequest req = new MarkCourtEventHeldRequest();
        req.setOutcomeType(OutcomeType.ADJOURNED_NO_PROGRESS);
        req.setNextEventType(NextEventType.TARIK);
        req.setNextEventDate(LocalDate.now().plusDays(7));
        req.setOutcome("Adjourned — counter filed");

        CourtEventResponse response = courtEventService.markHeld(ev.getId(), req);

        assertEquals(CourtEventStatus.ADJOURNED, response.getStatus());
        verify(courtCaseService, never()).recordJudgmentInternal(any(), any(), any(), any(), any());
        // The diary loop continues: a TARIK next-event row is created
        verify(courtEventRepository, atLeastOnce()).save(argThat(e -> e.getSequenceNo() == 2));
    }

    // ========================================================================
    // Conflict policy — Peshi hard-blocks, Tarik warns and allows
    // ========================================================================

    @Test
    @DisplayName("Peshi with an advocate overlap is hard-blocked")
    void peshiConflictThrows() {
        CourtCase cc = courtCase(CourtCaseStage.HEARING_STAGE, CourtCaseStatus.ACTIVE);
        when(courtCaseRepository.findByOurCourtCaseRefAndFirmId(cc.getOurCourtCaseRef(), FIRM_ID))
                .thenReturn(Optional.of(cc));
        when(courtEventRepository.maxSequenceNo(cc.getId())).thenReturn(0);
        CourtEvent existing = event(CourtEventStatus.SCHEDULED);
        when(courtEventRepository.findConflicts(eq(FIRM_ID), any(), any(), any(), any(), isNull()))
                .thenReturn(List.of(existing));

        ScheduleCourtEventRequest req = new ScheduleCourtEventRequest();
        req.setEventType(CourtEventType.PESHI);
        req.setScheduledDate(LocalDate.now().plusDays(1));
        req.setScheduledTime(LocalTime.of(10, 0));
        req.setAttendingAdvocateId(UUID.randomUUID());

        assertThrows(BusinessRuleException.class,
                () -> courtEventService.scheduleEvent(cc.getOurCourtCaseRef(), req));
    }

    @Test
    @DisplayName("Tarik with an advocate overlap is allowed and surfaces a warning")
    void tarikConflictAllowedWithWarning() {
        CourtCase cc = courtCase(CourtCaseStage.HEARING_STAGE, CourtCaseStatus.ACTIVE);
        when(courtCaseRepository.findByOurCourtCaseRefAndFirmId(cc.getOurCourtCaseRef(), FIRM_ID))
                .thenReturn(Optional.of(cc));
        when(courtEventRepository.maxSequenceNo(cc.getId())).thenReturn(0);
        CourtEvent existing = event(CourtEventStatus.SCHEDULED);
        when(courtEventRepository.findConflicts(eq(FIRM_ID), any(), any(), any(), any(), isNull()))
                .thenReturn(List.of(existing));
        when(courtEventRepository.save(any(CourtEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        ScheduleCourtEventRequest req = new ScheduleCourtEventRequest();
        req.setEventType(CourtEventType.TARIK);
        req.setScheduledDate(LocalDate.now().plusDays(1));
        req.setScheduledTime(LocalTime.of(10, 0));
        req.setAttendingAdvocateId(UUID.randomUUID());

        CourtEventResponse response = courtEventService.scheduleEvent(cc.getOurCourtCaseRef(), req);

        assertNotNull(response.getConflictWarning());
        assertTrue(response.getConflictWarning().toLowerCase().contains("overlap"));
    }
}
