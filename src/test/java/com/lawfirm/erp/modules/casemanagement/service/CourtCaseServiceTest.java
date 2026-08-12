package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.casemanagement.dto.request.RecordJudgmentRequest;
import com.lawfirm.erp.modules.casemanagement.dto.response.CourtCaseResponse;
import com.lawfirm.erp.modules.casemanagement.dto.response.UpcomingAppealResponse;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.enums.*;
import com.lawfirm.erp.modules.casemanagement.repository.*;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourtCaseServiceTest {

    private static final UUID FIRM_ID = UUID.randomUUID();

    @Mock private CourtCaseRepository courtCaseRepository;
    @Mock private MatterRepository matterRepository;
    @Mock private CourtEventRepository courtEventRepository;
    @Mock private CourtCaseRoleRepository courtCaseRoleRepository;
    @Mock private MatterPartyRepository matterPartyRepository;
    @Mock private MatterTimelineRepository matterTimelineRepository;
    @Mock private AppealDeadlineEngine appealDeadlineEngine;
    @Mock private AuditService auditService;

    @InjectMocks
    private CourtCaseService courtCaseService;

    @BeforeEach
    void setUp() {
        FirmContextHolder.set(FIRM_ID, "APX");
    }

    @AfterEach
    void tearDown() {
        FirmContextHolder.clear();
    }

    private CourtCase courtCase(CourtLevel level, MatterType type, CourtCaseStage stage) {
        CourtCase cc = new CourtCase();
        cc.setId(UUID.randomUUID());
        cc.setFirmId(FIRM_ID);
        cc.setMatterId(UUID.randomUUID());
        cc.setCourtLevel(level);
        cc.setRelationType(RelationType.ORIGINAL);
        cc.setOurCourtCaseRef("APX-MAT-2026-00001-" + level.name().charAt(0) + "1");
        cc.setStage(stage);
        cc.setStatus(CourtCaseStatus.JUDGMENT_AWAITED);
        cc.setPartyIsState(true); // recorded once at creation
        return cc;
    }

    private Matter matter(MatterType type) {
        Matter m = new Matter();
        m.setId(UUID.randomUUID());
        m.setFirmId(FIRM_ID);
        m.setMatterNumber("APX-MAT-2026-00001");
        m.setMatterType(type);
        return m;
    }

    private void stubLookup(CourtCase cc, Matter m) {
        when(courtCaseRepository.findByOurCourtCaseRefAndFirmId(cc.getOurCourtCaseRef(), FIRM_ID))
                .thenReturn(Optional.of(cc));
        when(matterRepository.findById(cc.getMatterId())).thenReturn(Optional.of(m));
        when(courtCaseRepository.save(any(CourtCase.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ========================================================================
    // Judgment recording — deadline trusts the STORED partyIsState
    // ========================================================================

    @Test
    @DisplayName("recordJudgment computes the deadline from the stored partyIsState, not a request flag")
    void recordJudgmentUsesStoredPartyIsState() {
        CourtCase cc = courtCase(CourtLevel.DISTRICT, MatterType.CIVIL, CourtCaseStage.JUDGMENT_AWAITED);
        Matter m = matter(MatterType.CIVIL);
        stubLookup(cc, m);

        LocalDate judgmentDate = LocalDate.of(2026, 8, 5);
        LocalDate deadline = LocalDate.of(2026, 9, 4);
        when(appealDeadlineEngine.compute(eq(CourtLevel.DISTRICT), eq(MatterType.CIVIL), eq(true), eq(judgmentDate)))
                .thenReturn(deadline);

        RecordJudgmentRequest req = new RecordJudgmentRequest();
        req.setJudgmentDate(judgmentDate);
        req.setJudgmentSummary("Decree for plaintiff");

        CourtCaseResponse response = courtCaseService.recordJudgment(cc.getOurCourtCaseRef(), req);

        assertEquals(CourtCaseStatus.DECIDED, response.getStatus());
        assertEquals(CourtCaseStage.JUDGMENT_DELIVERED, response.getStage());
        assertEquals(deadline, response.getAppealDeadline());
        assertFalse(response.isAppealRequiresLeave());
        // The engine was asked with the STORED flag (cc.partyIsState=true)
        verify(appealDeadlineEngine).compute(CourtLevel.DISTRICT, MatterType.CIVIL, true, judgmentDate);
    }

    @Test
    @DisplayName("A High Court judgment is flagged as requiring leave to appeal")
    void highCourtJudgmentFlagsLeave() {
        CourtCase cc = courtCase(CourtLevel.HIGH, MatterType.CIVIL, CourtCaseStage.JUDGMENT_AWAITED);
        Matter m = matter(MatterType.CIVIL);
        stubLookup(cc, m);
        when(appealDeadlineEngine.compute(any(), any(), anyBoolean(), any()))
                .thenReturn(LocalDate.now().plusDays(35));

        RecordJudgmentRequest req = new RecordJudgmentRequest();
        req.setJudgmentDate(LocalDate.of(2026, 8, 5));
        req.setJudgmentSummary("Decree");

        CourtCaseResponse response = courtCaseService.recordJudgment(cc.getOurCourtCaseRef(), req);

        assertTrue(response.isAppealRequiresLeave());
    }

    @Test
    @DisplayName("recordJudgment is rejected outside JUDGMENT_AWAITED")
    void recordJudgmentAtWrongStageThrows() {
        CourtCase cc = courtCase(CourtLevel.DISTRICT, MatterType.CIVIL, CourtCaseStage.FILED);
        Matter m = matter(MatterType.CIVIL);
        stubLookup(cc, m);

        RecordJudgmentRequest req = new RecordJudgmentRequest();
        req.setJudgmentDate(LocalDate.of(2026, 8, 5));

        assertThrows(BusinessRuleException.class,
                () -> courtCaseService.recordJudgment(cc.getOurCourtCaseRef(), req));
    }

    // ========================================================================
    // Allowed stages
    // ========================================================================

    @Test
    @DisplayName("allowedStages returns the transitions valid for this level/type/relation")
    void allowedStagesFiltersByValidity() {
        // District criminal, JUDGMENT_DELIVERED → FURTHER_APPEALED and REMANDED are
        // appellate-level only and are filtered out for a trial-level case
        CourtCase cc = courtCase(CourtLevel.DISTRICT, MatterType.CRIMINAL, CourtCaseStage.JUDGMENT_DELIVERED);
        Matter m = matter(MatterType.CRIMINAL);
        stubLookup(cc, m);

        List<CourtCaseStage> stages = courtCaseService.getAllowedStages(cc.getOurCourtCaseRef());

        assertEquals(4, stages.size());
        assertTrue(stages.contains(CourtCaseStage.APPEALED));
        assertTrue(stages.contains(CourtCaseStage.SENTENCING));
        assertTrue(stages.contains(CourtCaseStage.EXECUTION));
        assertTrue(stages.contains(CourtCaseStage.CLOSED));
        assertFalse(stages.contains(CourtCaseStage.FURTHER_APPEALED));
        assertFalse(stages.contains(CourtCaseStage.REMANDED));
    }

    // ========================================================================
    // Appeal-deadline watch
    // ========================================================================

    @Test
    @DisplayName("upcoming appeal deadlines exclude cases that already have an appeal filed")
    void upcomingAppealDeadlinesExcludesCasesWithChildren() {
        CourtCase due = courtCase(CourtLevel.DISTRICT, MatterType.CIVIL, CourtCaseStage.JUDGMENT_DELIVERED);
        due.setJudgmentDate(LocalDate.now().minusDays(10));
        due.setAppealDeadline(LocalDate.now().plusDays(20));
        CourtCase withChild = courtCase(CourtLevel.DISTRICT, MatterType.CIVIL, CourtCaseStage.JUDGMENT_DELIVERED);
        withChild.setJudgmentDate(LocalDate.now().minusDays(5));
        withChild.setAppealDeadline(LocalDate.now().plusDays(25));

        when(courtCaseRepository.findByFirmIdAndStatusAndAppealDeadlineBetweenAndAppealLapsedFalse(
                eq(FIRM_ID), eq(CourtCaseStatus.DECIDED), any(), any()))
                .thenReturn(List.of(due, withChild));
        when(courtCaseRepository.existsByParentCourtCaseId(withChild.getId())).thenReturn(true);
        when(courtCaseRepository.existsByParentCourtCaseId(due.getId())).thenReturn(false);

        Matter m = matter(MatterType.CIVIL);
        when(matterRepository.findAllById(anyCollection())).thenReturn(List.of(m));

        List<UpcomingAppealResponse> result = courtCaseService.listUpcomingAppealDeadlines(30);

        assertEquals(1, result.size());
        assertEquals(due.getOurCourtCaseRef(), result.get(0).getOurCourtCaseRef());
        assertEquals(due.getAppealDeadline(), result.get(0).getAppealDeadline());
    }
}
