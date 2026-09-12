package com.lawfirm.erp.modules.notification.scheduler;

import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import com.lawfirm.erp.modules.notification.event.NotificationEvent;
import com.lawfirm.erp.modules.notification.service.NotificationOrchestrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrialExpirySchedulerTest {

    @Mock FirmRepository firmRepository;
    @Mock NotificationOrchestrator notificationOrchestrator;
    @InjectMocks TrialExpiryScheduler scheduler;

    private Firm trialFirm(UUID id, LocalDateTime expiresAt, FirmStatus status) {
        Firm f = new Firm();
        f.setId(id);
        f.setLawFirmCode("TEST01");
        f.setName("Test Firm");
        f.setStatus(status);
        f.setIsTrial(true);
        f.setTrialExpiresAt(expiresAt);
        return f;
    }

    @Test
    void sendsExpiringNotification_when3DaysLeft() {
        UUID id = UUID.randomUUID();
        Firm f = trialFirm(id, LocalDateTime.now().plusDays(3), FirmStatus.TRIAL);
        when(firmRepository.findByIsTrialTrue()).thenReturn(List.of(f));

        scheduler.checkTrialExpiries();

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationOrchestrator).process(captor.capture());
        assertEquals(NotificationType.TRIAL_EXPIRING, captor.getValue().type());
    }

    @Test
    void sendsExpiredNotification_andSuspends_whenTrialEnded() {
        UUID id = UUID.randomUUID();
        Firm f = trialFirm(id, LocalDateTime.now().minusDays(1), FirmStatus.TRIAL);
        when(firmRepository.findByIsTrialTrue()).thenReturn(List.of(f));

        scheduler.checkTrialExpiries();

        // Firm should be suspended
        verify(firmRepository).save(argThat(firm -> firm.getStatus() == FirmStatus.SUSPENDED));

        // Notification should be sent
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationOrchestrator).process(captor.capture());
        assertEquals(NotificationType.TRIAL_EXPIRED, captor.getValue().type());
    }

    @Test
    void doesNotSendNotification_whenMoreThan3DaysLeft() {
        UUID id = UUID.randomUUID();
        Firm f = trialFirm(id, LocalDateTime.now().plusDays(10), FirmStatus.TRIAL);
        when(firmRepository.findByIsTrialTrue()).thenReturn(List.of(f));

        scheduler.checkTrialExpiries();

        verify(notificationOrchestrator, never()).process(any());
        verify(firmRepository, never()).save(any());
    }

    @Test
    void skipsFirmWithNullExpiry() {
        UUID id = UUID.randomUUID();
        Firm f = trialFirm(id, null, FirmStatus.TRIAL);
        when(firmRepository.findByIsTrialTrue()).thenReturn(List.of(f));

        scheduler.checkTrialExpiries();

        verify(notificationOrchestrator, never()).process(any());
    }
}
