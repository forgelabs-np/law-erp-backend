package com.lawfirm.erp.modules.notification.service;

import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.modules.notification.entity.Notification;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import com.lawfirm.erp.modules.notification.event.NotificationEvent;
import com.lawfirm.erp.modules.notification.repository.NotificationDeliveryRepository;
import com.lawfirm.erp.modules.notification.repository.NotificationRepository;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationOrchestratorTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationRenderer renderer;
    @Mock private NotificationPreferenceService preferenceService;
    @Mock private NotificationDeliveryRepository deliveryRepository;

    @InjectMocks private NotificationOrchestrator orchestrator;

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MATTER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Per-row save contract: id must round-trip so delivery rows can reference it.
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(preferenceService.isEmailEnabledFor(any(), any())).thenReturn(false);
        when(renderer.render(any(), any())).thenReturn(new RenderedNotification("T", "B"));
    }

    private Notification singleSavedRow() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Direct user targeting persists one notification with category pinned from type")
    void directUser_persistsOneNotification() {
        NotificationEvent event = NotificationEvent.toUser(FIRM_ID, USER_ID,
                NotificationType.CASE_ASSIGNED, "MATTER", MATTER_ID,
                Map.of("matterNumber", "APX-MAT-2026-00007", "assignmentRole", "PRIMARY_ADVOCATE"));

        orchestrator.process(event);

        Notification n = singleSavedRow();
        assertEquals(USER_ID, n.getRecipientUserId());
        assertEquals(FIRM_ID, n.getFirmId());
        assertEquals(NotificationType.CASE_ASSIGNED, n.getType());
        assertEquals("T", n.getTitle());
        assertEquals("B", n.getBody());
        assertEquals("MATTER", n.getReferenceType());
        assertEquals(MATTER_ID, n.getReferenceId());
        verify(userRepository, never()).findUserIdsByFirmIdAndRoleCode(any(), any());
        verify(userRepository, never()).findUserIdsByFirmId(any());
        verify(deliveryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Role targeting fans out to every user holding the role in the firm")
    void roleTargeting_fansOut() {
        UUID advocate1 = UUID.randomUUID();
        UUID advocate2 = UUID.randomUUID();
        NotificationEvent event = NotificationEvent.toRole(FIRM_ID, "FIRM_ADMIN",
                NotificationType.INVOICE_STATUS, "INVOICE", UUID.randomUUID(), Map.of());
        when(userRepository.findUserIdsByFirmIdAndRoleCode(FIRM_ID, "FIRM_ADMIN"))
                .thenReturn(List.of(advocate1, advocate2));

        orchestrator.process(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertEquals(List.of(advocate1, advocate2),
                captor.getAllValues().stream().map(Notification::getRecipientUserId).toList());
    }

    @Test
    @DisplayName("Dedup: event with a dedupKey already claimed by recipient is skipped entirely")
    void dedupKey_claimed_skipsRecipient() {
        NotificationEvent event = new NotificationEvent(FIRM_ID, USER_ID, null, false,
                NotificationType.CASE_ASSIGNED, "MATTER", MATTER_ID,
                "CASE_ASSIGNED:MATTER:" + MATTER_ID + ":" + USER_ID, Map.of());
        when(notificationRepository.existsByDedupKeyAndRecipientUserId(event.dedupKey(), USER_ID))
                .thenReturn(true);

        orchestrator.process(event);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fan-out with zero resolved recipients is a silent no-op (no save, no render)")
    void zeroRecipients_noOp() {
        NotificationEvent event = NotificationEvent.toRole(FIRM_ID, "GHOST_ROLE",
                NotificationType.INVOICE_STATUS, null, null, Map.of());
        when(userRepository.findUserIdsByFirmIdAndRoleCode(FIRM_ID, "GHOST_ROLE"))
                .thenReturn(List.of());

        orchestrator.process(event);

        verify(renderer, never()).render(any(), any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Event with no targeting mode at all is rejected loudly — producer bug")
    void noTargeting_throws() {
        NotificationEvent event = new NotificationEvent(FIRM_ID, null, null, false,
                NotificationType.CASE_ASSIGNED, null, null, null, Map.of());

        assertThrows(IllegalArgumentException.class, () -> orchestrator.process(event));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rendered rows carry the event's dedupKey so idempotency is auditable")
    void dedupKey_storedOnRows() {
        String key = "CASE_ASSIGNED:MATTER:" + MATTER_ID + ":" + USER_ID;
        NotificationEvent event = new NotificationEvent(FIRM_ID, USER_ID, null, false,
                NotificationType.CASE_ASSIGNED, "MATTER", MATTER_ID, key, Map.of());

        orchestrator.process(event);

        assertEquals(key, singleSavedRow().getDedupKey());
    }
}
