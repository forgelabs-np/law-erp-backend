package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.casemanagement.entity.AppealDeadlineRule;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStage;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import com.lawfirm.erp.modules.casemanagement.repository.AppealDeadlineRuleRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.modules.notification.event.NotificationEvent;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppealDeadlineNotificationTest {

    @Mock private AppealDeadlineRuleRepository ruleRepository;
    @Mock private CourtCaseRepository courtCaseRepository;
    @Mock private MatterRepository matterRepository;
    @Mock private AuditService auditService;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private AppealDeadlineEngine engine;

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID MATTER_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID ADVOCATE_ID = UUID.randomUUID();

    private CourtCase cc;
    private Matter matter;

    @BeforeEach
    void setUp() {
        Firm firm = new Firm();
        firm.setId(FIRM_ID);

        cc = new CourtCase();
        cc.setId(CASE_ID);
        cc.setFirmId(FIRM_ID);
        cc.setMatterId(MATTER_ID);
        cc.setOurCourtCaseRef("E2EFIRM-MAT-2026-00001-DC1");
        cc.setCourtName("Kathmandu District Court");
        cc.setStage(CourtCaseStage.JUDGMENT_DELIVERED);
        cc.setStatus(CourtCaseStatus.DECIDED);
        cc.setAppealDeadline(LocalDate.now().plusDays(3));
        cc.setAppealLapsed(false);
        cc.setAdvocateId(ADVOCATE_ID);

        matter = new Matter();
        matter.setId(MATTER_ID);
        matter.setFirmId(FIRM_ID);
        matter.setMatterNumber("E2EFIRM-MAT-2026-00001");
        matter.setTitle("Land dispute");
        matter.setMatterType(MatterType.CIVIL);
        matter.setStatus(MatterStatus.ACTIVE);

        User advocate = new User();
        advocate.setId(ADVOCATE_ID);
        advocate.setEmail("adv@firm.com");
        advocate.setFirm(firm);
        advocate.setUserType(UserType.FIRM_USER);

        when(courtCaseRepository.findByStatusAndAppealDeadlineBefore(
                eq(CourtCaseStatus.DECIDED), any(LocalDate.class)))
                .thenReturn(List.of(cc));
        when(courtCaseRepository.existsByParentCourtCaseId(CASE_ID)).thenReturn(false);
        when(matterRepository.findById(MATTER_ID)).thenReturn(Optional.of(matter));
        when(matterRepository.findAllById(any())).thenReturn(List.of(matter));
        when(userRepository.findAllById(any())).thenReturn(List.of(advocate));
        when(courtCaseRepository.save(any(CourtCase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(matterRepository.save(any(Matter.class))).thenAnswer(inv -> inv.getArgument(0));
        when(courtCaseRepository.findByMatterIdAndFirmIdOrderByCreatedAtAsc(MATTER_ID, FIRM_ID))
                .thenReturn(List.of());

        AppealDeadlineRule rule = new AppealDeadlineRule();
        rule.setDays(30);
        rule.setExtensionDays(15);
        when(ruleRepository.findByCourtLevelAppealedFromAndMatterTypeAndPartyIsState(
                CourtLevel.DISTRICT, MatterType.CIVIL, false)).thenReturn(Optional.of(rule));
    }

    @SuppressWarnings("unchecked")
    private List<NotificationEvent> publishedNotificationEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .filter(NotificationEvent.class::isInstance)
                .map(NotificationEvent.class::cast)
                .collect(Collectors.toList());
    }

    @Test
    @DisplayName("T-3: publishes APPEAL_DEADLINE alert to the case's advocate with day-bucketed dedup key")
    void t3_publishesDeadlineAlert() {
        engine.checkUpcomingDeadlines();

        List<NotificationEvent> events = publishedNotificationEvents();
        assertEquals(1, events.size());
        NotificationEvent event = events.get(0);
        assertEquals(NotificationType.APPEAL_DEADLINE, event.type());
        assertEquals(ADVOCATE_ID, event.recipientUserId());
        assertEquals(FIRM_ID, event.firmId());
        assertEquals("COURT_CASE", event.referenceType());
        assertEquals(CASE_ID, event.referenceId());
        assertNotNull(event.dedupKey());
        assertTrue(event.dedupKey().contains(cc.getAppealDeadline().toString()),
                "dedup key bucketed by deadline date");
        assertEquals("E2EFIRM-MAT-2026-00001", event.variables().get("matterNumber"));
        assertEquals("3", event.variables().get("daysRemaining"));
    }

    @Test
    @DisplayName("T-3: dedup key is stable across runs so downstream dedup suppresses re-runs")
    void t3_dedupKeyStableAcrossRuns() {
        cc.setAppealDeadline(LocalDate.now().plusDays(1)); // already inside T-3 window

        engine.checkUpcomingDeadlines();
        engine.checkUpcomingDeadlines();

        List<NotificationEvent> events = publishedNotificationEvents();
        assertEquals(2, events.size(), "engine publishes per run — orchestrator dedups by key");
        assertEquals(events.get(0).dedupKey(), events.get(1).dedupKey(),
                "same case + deadline → same dedupKey");
    }

    @Test
    @DisplayName("Lapse: publishes APPEAL_LAPSED notice to FIRM_ADMIN")
    void lapse_publishesFirmAdminNotice() {
        cc.setAppealDeadline(LocalDate.now().minusDays(1));

        engine.closeLapsedAppeals();

        List<NotificationEvent> events = publishedNotificationEvents();
        assertEquals(1, events.size());
        NotificationEvent event = events.get(0);
        assertEquals(NotificationType.APPEAL_LAPSED, event.type());
        assertEquals("FIRM_ADMIN", event.recipientRoleCode());
        assertNull(event.recipientUserId());
        assertEquals(CASE_ID, event.referenceId());
    }
}
