package com.lawfirm.erp.modules.notification.dispatcher;

import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.modules.email.service.EmailService;
import com.lawfirm.erp.modules.notification.entity.Notification;
import com.lawfirm.erp.modules.notification.entity.NotificationDelivery;
import com.lawfirm.erp.modules.notification.enums.DeliveryChannel;
import com.lawfirm.erp.modules.notification.enums.DeliveryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationDispatcherTest {

    @Mock private EmailService emailService;
    @Mock private UserRepository userRepository;

    @InjectMocks private EmailNotificationDispatcher dispatcher;

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private Notification notification() {
        return Notification.builder()
                .firmId(FIRM_ID)
                .recipientUserId(USER_ID)
                .title("Assigned to matter")
                .body("You were assigned to matter M-1.")
                .build();
    }

    @Test
    @DisplayName("Happy path marks delivery SENT with deliveredAt and clears error")
    void success_marksSent() {
        Notification n = notification();
        NotificationDelivery d = NotificationDelivery.builder()
                .firmId(FIRM_ID).notificationId(UUID.randomUUID())
                .recipientUserId(USER_ID).channel(DeliveryChannel.EMAIL)
                .status(DeliveryStatus.PENDING).build();
        User u = new User();
        u.setEmail("a@b.com");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(u));
        when(emailService.sendNotificationEmail(any(), any(), anyString(), anyString(), any()))
                .thenReturn(true);

        NotificationDelivery result = dispatcher.dispatch(n, d);

        assertEquals(DeliveryStatus.SENT, result.getStatus());
        assertNotNull(result.getDeliveredAt());
        assertNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("SMTP failure marks delivery FAILED with an error message")
    void smtpFailure_marksFailed() {
        Notification n = notification();
        NotificationDelivery d = NotificationDelivery.builder()
                .firmId(FIRM_ID).notificationId(UUID.randomUUID())
                .recipientUserId(USER_ID).channel(DeliveryChannel.EMAIL)
                .status(DeliveryStatus.PENDING).build();
        User u = new User();
        u.setEmail("a@b.com");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(u));
        when(emailService.sendNotificationEmail(any(), any(), anyString(), anyString(), any()))
                .thenReturn(false);

        NotificationDelivery result = dispatcher.dispatch(n, d);

        assertEquals(DeliveryStatus.FAILED, result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertNull(result.getDeliveredAt());
    }

    @Test
    @DisplayName("Missing recipient user or email → FAILED without attempting a send")
    void missingRecipient_fails() {
        Notification n = notification();
        NotificationDelivery d = NotificationDelivery.builder()
                .firmId(FIRM_ID).notificationId(UUID.randomUUID())
                .recipientUserId(USER_ID).channel(DeliveryChannel.EMAIL)
                .status(DeliveryStatus.PENDING).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        NotificationDelivery result = dispatcher.dispatch(n, d);

        assertEquals(DeliveryStatus.FAILED, result.getStatus());
        verify(emailService, never())
                .sendNotificationEmail(any(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Dispatcher never throws even when the email service explodes")
    void emailServiceThrows_swallowed() {
        Notification n = notification();
        NotificationDelivery d = NotificationDelivery.builder()
                .firmId(FIRM_ID).notificationId(UUID.randomUUID())
                .recipientUserId(USER_ID).channel(DeliveryChannel.EMAIL)
                .status(DeliveryStatus.PENDING).build();
        User u = new User();
        u.setEmail("a@b.com");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(u));
        when(emailService.sendNotificationEmail(any(), any(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("SMTP host unreachable"));

        NotificationDelivery result = assertDoesNotThrow(() -> dispatcher.dispatch(n, d));

        assertEquals(DeliveryStatus.FAILED, result.getStatus());
    }
}
