package com.lawfirm.erp.modules.notification.event;

import com.lawfirm.erp.modules.notification.service.NotificationOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationOrchestrator orchestrator;

    @InjectMocks
    private NotificationEventListener listener;

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("onNotificationEvent delegates to orchestrator")
    void delegatesToOrchestrator() {
        NotificationEvent event = NotificationEvent.toUser(FIRM_ID, USER_ID,
                com.lawfirm.erp.modules.notification.enums.NotificationType.CASE_ASSIGNED,
                null, null, java.util.Map.of());

        listener.onNotificationEvent(event);

        verify(orchestrator).process(event);
    }

    @Test
    @DisplayName("Orchestrator exceptions are swallowed — producers must never break")
    void orchestratorFailure_isSwallowed() {
        NotificationEvent event = NotificationEvent.toUser(FIRM_ID, USER_ID,
                com.lawfirm.erp.modules.notification.enums.NotificationType.CASE_ASSIGNED,
                null, null, java.util.Map.of());
        doThrow(new RuntimeException("DB down")).when(orchestrator).process(any());

        assertDoesNotThrow(() -> listener.onNotificationEvent(event));
    }
}
