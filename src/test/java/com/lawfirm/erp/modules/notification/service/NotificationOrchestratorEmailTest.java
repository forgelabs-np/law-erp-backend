package com.lawfirm.erp.modules.notification.service;

import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.modules.notification.entity.Notification;
import com.lawfirm.erp.modules.notification.entity.NotificationDelivery;
import com.lawfirm.erp.modules.notification.enums.DeliveryChannel;
import com.lawfirm.erp.modules.notification.enums.DeliveryStatus;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import com.lawfirm.erp.modules.notification.event.NotificationEvent;
import com.lawfirm.erp.modules.notification.repository.NotificationDeliveryRepository;
import com.lawfirm.erp.modules.notification.repository.NotificationRepository;
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

/**
 * Email-channel integration: ALERT types enqueue delivery rows, SYSTEM
 * types don't (unless opted in), preferences gate everything.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationOrchestratorEmailTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationRenderer renderer;
    @Mock private NotificationPreferenceService preferenceService;
    @Mock private NotificationDeliveryRepository deliveryRepository;

    @InjectMocks private NotificationOrchestrator orchestrator;

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private void stubInAppSave() {
        // Orchestrator saves per row (needs generated ids for delivery FKs)
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });
        when(renderer.render(any(), any())).thenReturn(new RenderedNotification("T", "B"));
    }

    @Test
    @DisplayName("ALERT event enqueues one PENDING email delivery per recipient")
    void alert_enqueuesEmailDelivery() {
        stubInAppSave();
        when(preferenceService.isEmailEnabledFor(USER_ID, NotificationType.HEARING_REMINDER)).thenReturn(true);

        NotificationEvent event = NotificationEvent.toUser(FIRM_ID, USER_ID,
                NotificationType.HEARING_REMINDER, "COURT_EVENT", UUID.randomUUID(),
                Map.of("matterNumber", "M-1", "courtName", "Kathmandu DC", "hearingTime", "10:30"));
        when(userRepository.findUserIdsByFirmIdAndRoleCode(any(), any())).thenReturn(List.of());

        orchestrator.process(event);

        ArgumentCaptor<List<NotificationDelivery>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(captor.capture());
        List<NotificationDelivery> rows = captor.getValue();
        assertEquals(1, rows.size());
        NotificationDelivery d = rows.get(0);
        assertEquals(DeliveryChannel.EMAIL, d.getChannel());
        assertEquals(DeliveryStatus.PENDING, d.getStatus());
        assertEquals(FIRM_ID, d.getFirmId());
        assertNotNull(d.getNextAttemptAt(), "due immediately — sweep picks it up within a minute");
    }

    @Test
    @DisplayName("SYSTEM event enqueues no email delivery (in-app only by default)")
    void system_noEmailByDefault() {
        stubInAppSave();
        when(preferenceService.isEmailEnabledFor(USER_ID, NotificationType.CASE_ASSIGNED)).thenReturn(false);

        orchestrator.process(NotificationEvent.toUser(FIRM_ID, USER_ID,
                NotificationType.CASE_ASSIGNED, "MATTER", UUID.randomUUID(), Map.of()));

        verify(deliveryRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Preference opt-in emails a SYSTEM notification too")
    void system_optIn_enqueuesEmail() {
        stubInAppSave();
        when(preferenceService.isEmailEnabledFor(USER_ID, NotificationType.CASE_ASSIGNED)).thenReturn(true);

        orchestrator.process(NotificationEvent.toUser(FIRM_ID, USER_ID,
                NotificationType.CASE_ASSIGNED, "MATTER", UUID.randomUUID(), Map.of()));

        verify(deliveryRepository).saveAll(anyList());
    }
}
