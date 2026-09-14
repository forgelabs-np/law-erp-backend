package com.lawfirm.erp.modules.notification.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.modules.notification.dto.SendBroadcastRequest;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import com.lawfirm.erp.modules.notification.event.NotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BroadcastServiceTest {

    @Mock private NotificationOrchestrator orchestrator;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private UserRepository userRepository;

    @InjectMocks private BroadcastService service;

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    private SendBroadcastRequest request(String audience) {
        SendBroadcastRequest req = new SendBroadcastRequest();
        req.setTitle("Firm closed on dashain");
        req.setBody("The office will be closed on the 15th.");
        req.setAudience(audience);
        return req;
    }

    @BeforeEach
    void setUp() {
        when(currentUserResolver.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(currentUserResolver.getCurrentFirmId()).thenReturn(FIRM_ID);
    }

    @Test
    @DisplayName("ALL: processes the event synchronously with allFirmUsers targeting")
    void broadcastAll() {

        service.send(request("ALL"));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(orchestrator).process(captor.capture());
        NotificationEvent event = captor.getValue();
        assertTrue(event.allFirmUsers());
        assertEquals(NotificationType.ANNOUNCEMENT, event.type());
        assertEquals(FIRM_ID, event.firmId());
        assertEquals(FIRM_ID.toString(), event.referenceId().toString(), "referenceId = firm id");
        assertEquals("FIRM", event.referenceType());
        assertEquals("Firm closed on dashain", event.variables().get("title"));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("ROLE: targets a single firm-scoped role code")
    void broadcastRole() {
        when(userRepository.findUserIdsByFirmIdAndRoleCode(FIRM_ID, "ADVOCATE"))
                .thenReturn(java.util.List.of(UUID.randomUUID()));

        service.send(request("ADVOCATE"));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(orchestrator).process(captor.capture());
        assertEquals("ADVOCATE", captor.getValue().recipientRoleCode());
    }

    @Test
    @DisplayName("Invalid audience → BusinessRuleException")
    void invalidAudience_rejected() {

        when(userRepository.findUserIdsByFirmIdAndRoleCode(eq(FIRM_ID), anyString()))
                .thenReturn(java.util.List.of());

        assertThrows(BusinessRuleException.class, () -> service.send(request("EVERYONE")));
    }

    @Test
    @DisplayName("Firm-admin scoping: event always carries the caller's firm")
    void scopedToFirm() {

        service.send(request("ALL"));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(orchestrator).process(captor.capture());
        assertEquals(FIRM_ID, captor.getValue().firmId());
    }
}
