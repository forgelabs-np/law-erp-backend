package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.CourtEvent;
import com.lawfirm.erp.modules.casemanagement.entity.HearingReminderLog;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.entity.MatterParty;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventType;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtEventRepository;
import com.lawfirm.erp.modules.casemanagement.repository.HearingReminderLogRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterPartyRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.modules.email.dto.HearingReminderDetails;
import com.lawfirm.erp.modules.email.service.EmailService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HearingReminderServiceImplTest {

    @Mock private CourtEventRepository courtEventRepository;
    @Mock private CourtCaseRepository courtCaseRepository;
    @Mock private MatterRepository matterRepository;
    @Mock private MatterPartyRepository matterPartyRepository;
    @Mock private UserRepository userRepository;
    @Mock private FirmRepository firmRepository;
    @Mock private HearingReminderLogRepository reminderLogRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private HearingReminderServiceImpl hearingReminderService;

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID ADVOCATE_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();

    private CourtEvent event;
    private User advocate;
    private User client;
    private MatterParty ourClientParty;

    @BeforeEach
    void setUp() {
        CourtCase cc = new CourtCase();
        cc.setId(UUID.randomUUID());
        cc.setMatterId(UUID.randomUUID());
        cc.setFirmId(FIRM_ID);
        cc.setOurCourtCaseRef("E2EFIRM-MAT-2026-00001-DC1");
        cc.setCourtName("Kathmandu District Court");

        Matter matter = new Matter();
        matter.setId(cc.getMatterId());
        matter.setFirmId(FIRM_ID);
        matter.setMatterNumber("E2EFIRM-MAT-2026-00001");
        matter.setTitle("Test matter");

        Firm firm = new Firm();
        firm.setId(FIRM_ID);
        firm.setName("E2EFIRM");

        event = new CourtEvent();
        event.setId(UUID.randomUUID());
        event.setFirmId(FIRM_ID);
        event.setCourtCaseId(cc.getId());
        event.setEventType(CourtEventType.PESHI);
        event.setStatus(CourtEventStatus.SCHEDULED);
        event.setScheduledDate(TOMORROW);
        event.setAttendingAdvocateId(ADVOCATE_ID);
        event.setCourtRoom("Room 5");
        event.setJudgeName("Hon. Judge");

        advocate = new User();
        advocate.setId(ADVOCATE_ID);
        advocate.setEmail("advocate@e2efirm.com");
        advocate.setFullName("E2E Advocate");

        client = new User();
        client.setId(CLIENT_ID);
        client.setEmail("client@e2efirm.com");
        client.setFullName("E2E Client");

        ourClientParty = new MatterParty();
        ourClientParty.setId(UUID.randomUUID());
        ourClientParty.setMatterId(matter.getId());
        ourClientParty.setClientId(CLIENT_ID);
        ourClientParty.setOurClient(true);
        ourClientParty.setFullName("E2E Client");

        when(courtEventRepository.findByEventTypeAndStatusAndScheduledDate(
                CourtEventType.PESHI, CourtEventStatus.SCHEDULED, TOMORROW))
                .thenReturn(List.of(event));
        when(courtCaseRepository.findAllById(any())).thenReturn(List.of(cc));
        when(matterRepository.findAllById(any())).thenReturn(List.of(matter));
        when(firmRepository.findAllById(any())).thenReturn(List.of(firm));
        when(matterPartyRepository.findByMatterIdInAndFirmIdAndOurClientTrue(List.of(matter.getId()), FIRM_ID))
                .thenReturn(List.of(ourClientParty));
        when(userRepository.findAllById(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Iterable<UUID> ids = inv.getArgument(0);
            List<User> result = new java.util.ArrayList<>();
            for (UUID id : ids) {
                result.add(id.equals(ADVOCATE_ID) ? advocate : client);
            }
            return result;
        });
        when(reminderLogRepository.save(any(HearingReminderLog.class)))
                .thenAnswer(inv -> {
                    HearingReminderLog l = inv.getArgument(0);
                    l.setId(UUID.randomUUID());
                    return l;
                });

        // Models the DB unique constraint: the second claim for the same
        // (event, type, email, date) is rejected.
        when(reminderLogRepository.existsByCourtEventIdAndRecipientTypeAndRecipientEmailAndScheduledDate(
                any(), any(), any(), any()))
                .thenAnswer(inv -> !claimed.add(inv.getArgument(0) + "|" + inv.getArgument(2)));
    }

    private final java.util.Set<String> claimed = new java.util.HashSet<>();

    @Test
    @DisplayName("Sends reminder to attending advocate AND every linked our-client")
    void sendsToAdvocateAndClients() {
        int sent = hearingReminderService.sendRemindersForDate(TOMORROW);

        assertEquals(2, sent);
        verify(emailService).sendHearingReminder(eq(FIRM_ID), eq(ADVOCATE_ID), eq("advocate@e2efirm.com"),
                eq("E2E Advocate"), any(HearingReminderDetails.class),
                eq(HearingReminderLog.RecipientType.ADVOCATE), any(UUID.class));
        verify(emailService).sendHearingReminder(eq(FIRM_ID), eq(CLIENT_ID), eq("client@e2efirm.com"),
                eq("E2E Client"), any(HearingReminderDetails.class),
                eq(HearingReminderLog.RecipientType.CLIENT), any(UUID.class));
        verify(reminderLogRepository, times(2)).save(any(HearingReminderLog.class));
    }

    @Test
    @DisplayName("Details carry matter number, ref, court name, date and time")
    void detailsAreComplete() {
        event.setScheduledTime(java.time.LocalTime.of(10, 30));

        hearingReminderService.sendRemindersForDate(TOMORROW);

        ArgumentCaptor<HearingReminderDetails> captor = ArgumentCaptor.forClass(HearingReminderDetails.class);
        verify(emailService, atLeastOnce()).sendHearingReminder(
                eq(FIRM_ID), any(), any(), any(), captor.capture(), any(), any());
        HearingReminderDetails details = captor.getValue();
        assertEquals("E2EFIRM-MAT-2026-00001", details.matterNumber());
        assertEquals("E2EFIRM-MAT-2026-00001-DC1", details.courtCaseRef());
        assertEquals("Kathmandu District Court", details.courtName());
        assertEquals(TOMORROW, details.scheduledDate());
        assertEquals(java.time.LocalTime.of(10, 30), details.scheduledTime());
        assertEquals("Room 5", details.courtRoom());
        assertEquals("E2EFIRM", details.firmName());
    }

    @Test
    @DisplayName("Already-sent recipients are skipped (idempotent claim)")
    void skipsAlreadySent() {
        when(reminderLogRepository.existsByCourtEventIdAndRecipientTypeAndRecipientEmailAndScheduledDate(
                event.getId(), HearingReminderLog.RecipientType.ADVOCATE, "advocate@e2efirm.com", TOMORROW))
                .thenReturn(true);

        int sent = hearingReminderService.sendRemindersForDate(TOMORROW);

        assertEquals(1, sent);
        verify(emailService, never()).sendHearingReminder(
                eq(FIRM_ID), eq(ADVOCATE_ID), any(), any(), any(),
                eq(HearingReminderLog.RecipientType.ADVOCATE), any());
        verify(emailService).sendHearingReminder(
                eq(FIRM_ID), eq(CLIENT_ID), any(), any(), any(),
                eq(HearingReminderLog.RecipientType.CLIENT), any());
    }

    @Test
    @DisplayName("No attending advocate → only the client is reminded")
    void noAdvocateAssigned() {
        event.setAttendingAdvocateId(null);

        int sent = hearingReminderService.sendRemindersForDate(TOMORROW);

        assertEquals(1, sent);
        verify(emailService, never()).sendHearingReminder(
                any(), any(), any(), any(), any(),
                eq(HearingReminderLog.RecipientType.ADVOCATE), any());
    }

    @Test
    @DisplayName("External party (not our client / no client account) gets no email")
    void externalPartySkipped() {
        // The repository query only returns our-client parties — no matches here
        when(matterPartyRepository.findByMatterIdInAndFirmIdAndOurClientTrue(
                List.of(ourClientParty.getMatterId()), FIRM_ID))
                .thenReturn(List.of());

        int sent = hearingReminderService.sendRemindersForDate(TOMORROW);

        assertEquals(1, sent); // advocate only
        verify(emailService, never()).sendHearingReminder(
                any(), any(), any(), any(), any(),
                eq(HearingReminderLog.RecipientType.CLIENT), any());
    }

    @Test
    @DisplayName("Same client linked twice on the matter → still one email (claim dedupes by email)")
    void duplicateClientPartySendsOnce() {
        MatterParty secondParty = new MatterParty();
        secondParty.setId(UUID.randomUUID());
        secondParty.setMatterId(ourClientParty.getMatterId());
        secondParty.setClientId(CLIENT_ID);
        secondParty.setOurClient(true);
        secondParty.setFullName("E2E Client (duplicate)");

        when(matterPartyRepository.findByMatterIdInAndFirmIdAndOurClientTrue(
                List.of(ourClientParty.getMatterId()), FIRM_ID))
                .thenReturn(List.of(ourClientParty, secondParty));

        int sent = hearingReminderService.sendRemindersForDate(TOMORROW);

        assertEquals(2, sent); // advocate + client (once)
        verify(emailService, times(1)).sendHearingReminder(
                eq(FIRM_ID), eq(CLIENT_ID), eq("client@e2efirm.com"), any(), any(),
                eq(HearingReminderLog.RecipientType.CLIENT), any());
    }

    @Test
    @DisplayName("One event failing never stops the rest of the batch")
    void failureIsolatedPerEvent() {
        CourtEvent secondEvent = new CourtEvent();
        secondEvent.setId(UUID.randomUUID());
        secondEvent.setFirmId(FIRM_ID);
        secondEvent.setCourtCaseId(event.getCourtCaseId()); // same case/matter — fine
        secondEvent.setEventType(CourtEventType.PESHI);
        secondEvent.setStatus(CourtEventStatus.SCHEDULED);
        secondEvent.setScheduledDate(TOMORROW);
        secondEvent.setAttendingAdvocateId(ADVOCATE_ID);

        when(courtEventRepository.findByEventTypeAndStatusAndScheduledDate(
                CourtEventType.PESHI, CourtEventStatus.SCHEDULED, TOMORROW))
                .thenReturn(List.of(event, secondEvent));

        doThrow(new RuntimeException("SMTP down"))
                .doNothing()
                .when(emailService).sendHearingReminder(any(), any(), any(), any(), any(), any(), any());

        int sent = hearingReminderService.sendRemindersForDate(TOMORROW);

        // First event's advocate+client threw; second event's advocate+client succeeded
        assertEquals(2, sent);
    }

    @Test
    @DisplayName("No hearings on the date → nothing happens")
    void noEventsForDate() {
        when(courtEventRepository.findByEventTypeAndStatusAndScheduledDate(
                CourtEventType.PESHI, CourtEventStatus.SCHEDULED, TOMORROW))
                .thenReturn(List.of());

        int sent = hearingReminderService.sendRemindersForDate(TOMORROW);

        assertEquals(0, sent);
        verify(emailService, never()).sendHearingReminder(any(), any(), any(), any(), any(), any(), any());
        verify(reminderLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Scheduler only ever asks for SCHEDULED events (CANCELED/HELD excluded by query)")
    void onlyScheduledEventsQueried() {
        hearingReminderService.sendRemindersForDate(TOMORROW);

        verify(courtEventRepository).findByEventTypeAndStatusAndScheduledDate(
                CourtEventType.PESHI, CourtEventStatus.SCHEDULED, TOMORROW);
    }
}
