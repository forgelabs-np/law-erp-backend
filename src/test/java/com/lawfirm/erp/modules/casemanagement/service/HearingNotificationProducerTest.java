package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.modules.notification.event.NotificationEvent;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Producer wiring: the T-1 hearing reminder flow must ALSO publish in-app
 * HEARING_REMINDER events (emails keep flowing unchanged). Uses the same
 * arrange block as HearingReminderServiceImplTest.
 */
class HearingNotificationProducerTest extends HearingReminderServiceImplTest {

    @Test
    @DisplayName("Reminder run publishes one HEARING_REMINDER event per recipient")
    void publishesHearingReminderEvents() {
        int sent = hearingReminderService.sendRemindersForDate(TOMORROW);

        assertEquals(2, sent); // advocate + client emails still flow

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        List<NotificationEvent> events = captor.getAllValues().stream()
                .filter(NotificationEvent.class::isInstance)
                .map(NotificationEvent.class::cast)
                .toList();
        assertEquals(2, events.size(), "one event per reminder recipient");

        NotificationEvent advocateEvent = events.stream()
                .filter(e -> ADVOCATE_ID.equals(e.recipientUserId())).findFirst().orElseThrow();
        assertEquals(NotificationType.HEARING_REMINDER, advocateEvent.type());
        assertEquals(FIRM_ID, advocateEvent.firmId());
        assertEquals("COURT_EVENT", advocateEvent.referenceType());
        assertNotNull(advocateEvent.referenceId());
        assertNotNull(advocateEvent.dedupKey(), "scheduler must be idempotent via dedupKey");
        assertTrue(advocateEvent.dedupKey().contains(String.valueOf(TOMORROW)),
                "dedup key is day-bucketed");
        assertEquals("E2EFIRM-MAT-2026-00001", advocateEvent.variables().get("matterNumber"));
    }
}
