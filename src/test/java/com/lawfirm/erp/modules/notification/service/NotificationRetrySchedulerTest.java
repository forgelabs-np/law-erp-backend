package com.lawfirm.erp.modules.notification.service;

import com.lawfirm.erp.modules.notification.dispatcher.NotificationDispatcher;
import com.lawfirm.erp.modules.notification.entity.Notification;
import com.lawfirm.erp.modules.notification.entity.NotificationDelivery;
import com.lawfirm.erp.modules.notification.enums.DeliveryChannel;
import com.lawfirm.erp.modules.notification.enums.DeliveryStatus;
import com.lawfirm.erp.modules.notification.repository.NotificationDeliveryRepository;
import com.lawfirm.erp.modules.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationRetrySchedulerTest {

    @Mock private NotificationDeliveryRepository deliveryRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationDispatcher emailDispatcher;

    private NotificationRetryScheduler scheduler;

    private NotificationDelivery delivery;

    @BeforeEach
    void setUp() {
        // Must be stubbed BEFORE construction: the scheduler maps dispatchers by channel().
        when(emailDispatcher.channel()).thenReturn(DeliveryChannel.EMAIL);
        scheduler = new NotificationRetryScheduler(
                deliveryRepository, notificationRepository, List.of(emailDispatcher));

        delivery = NotificationDelivery.builder()
                .firmId(UUID.randomUUID()).notificationId(UUID.randomUUID())
                .recipientUserId(UUID.randomUUID())
                .channel(DeliveryChannel.EMAIL)
                .status(DeliveryStatus.RETRYING)
                .attempts(1)
                .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
                .build();
    }

    private void due(List<NotificationDelivery> rows) {
        when(deliveryRepository.findByStatusAndNextAttemptAtBefore(
                eq(DeliveryStatus.PENDING), any(LocalDateTime.class))).thenReturn(rows);
        when(deliveryRepository.findByStatusAndNextAttemptAtBefore(
                eq(DeliveryStatus.RETRYING), any(LocalDateTime.class))).thenReturn(List.of());
        when(notificationRepository.findById(any(UUID.class)))
                .thenReturn(Optional.of(Notification.builder().build()));
    }

    @Test
    @DisplayName("Dispatcher SENT → row saved terminal, no backoff scheduled")
    void sentIsTerminal() {
        due(List.of(delivery));
        when(emailDispatcher.channel()).thenReturn(DeliveryChannel.EMAIL);
        when(emailDispatcher.dispatch(any(), any())).thenAnswer(inv -> {
            NotificationDelivery d = inv.getArgument(1);
            d.setStatus(DeliveryStatus.SENT);
            return d;
        });

        scheduler.retryFailedDispatches();

        verify(deliveryRepository).save(delivery);
        assertEquals(DeliveryStatus.SENT, delivery.getStatus());
    }

    @Test
    @DisplayName("Dispatcher FAILED → attempts incremented, backoff scheduled, status RETRYING")
    void failedSchedulesBackoff() {
        due(List.of(delivery));
        when(emailDispatcher.channel()).thenReturn(DeliveryChannel.EMAIL);
        when(emailDispatcher.dispatch(any(), any())).thenAnswer(inv -> {
            NotificationDelivery d = inv.getArgument(1);
            d.setStatus(DeliveryStatus.FAILED);
            return d;
        });

        scheduler.retryFailedDispatches();

        assertEquals(DeliveryStatus.RETRYING, delivery.getStatus());
        assertEquals(2, delivery.getAttempts());
        assertNotNull(delivery.getNextAttemptAt());
        assertTrue(delivery.getNextAttemptAt().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("Attempt cap reached → DEAD, never rescheduled")
    void exhaustedGoesDead() {
        delivery.setAttempts(3); // cap is 3
        due(List.of(delivery));
        when(emailDispatcher.channel()).thenReturn(DeliveryChannel.EMAIL);
        when(emailDispatcher.dispatch(any(), any())).thenAnswer(inv -> {
            NotificationDelivery d = inv.getArgument(1);
            d.setStatus(DeliveryStatus.FAILED);
            return d;
        });

        scheduler.retryFailedDispatches();

        assertEquals(DeliveryStatus.DEAD, delivery.getStatus());
        assertNull(delivery.getNextAttemptAt());
    }

    @Test
    @DisplayName("One row's failure never stops the sweep")
    void sweepIsolatesRowFailures() {
        NotificationDelivery boom = NotificationDelivery.builder()
                .firmId(UUID.randomUUID()).notificationId(UUID.randomUUID())
                .recipientUserId(UUID.randomUUID())
                .channel(DeliveryChannel.EMAIL)
                .status(DeliveryStatus.PENDING)
                .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(deliveryRepository.findByStatusAndNextAttemptAtBefore(
                eq(DeliveryStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(boom, delivery));
        when(deliveryRepository.findByStatusAndNextAttemptAtBefore(
                eq(DeliveryStatus.RETRYING), any(LocalDateTime.class))).thenReturn(List.of());
        when(emailDispatcher.channel()).thenReturn(DeliveryChannel.EMAIL);
        when(emailDispatcher.dispatch(any(Notification.class), eq(boom)))
                .thenThrow(new RuntimeException("boom"));
        when(emailDispatcher.dispatch(any(Notification.class), eq(delivery))).thenAnswer(inv -> {
            NotificationDelivery d = inv.getArgument(1);
            d.setStatus(DeliveryStatus.SENT);
            d.setDeliveredAt(LocalDateTime.now());
            return d;
        });

        assertDoesNotThrow(scheduler::retryFailedDispatches);
        verify(deliveryRepository).save(delivery);
    }

    @Test
    @DisplayName("No dispatcher registered for a row's channel → row marked DEAD (no infinite loop)")
    void unknownChannelGoesDead() {
        delivery.setChannel(DeliveryChannel.SMS); // no SMS dispatcher exists
        due(List.of(delivery));

        scheduler.retryFailedDispatches();

        assertEquals(DeliveryStatus.DEAD, delivery.getStatus());
    }
}
