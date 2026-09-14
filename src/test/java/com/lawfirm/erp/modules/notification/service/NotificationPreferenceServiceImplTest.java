package com.lawfirm.erp.modules.notification.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.UnauthorizedException;
import com.lawfirm.erp.modules.notification.dto.UpsertNotificationPreferenceRequest;
import com.lawfirm.erp.modules.notification.entity.NotificationPreference;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import com.lawfirm.erp.modules.notification.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationPreferenceServiceImplTest {

    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private CurrentUserResolver currentUserResolver;

    @InjectMocks private NotificationPreferenceServiceImpl service;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(currentUserResolver.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    @DisplayName("No row → ALERT types default to email ON, others OFF")
    void defaults_byCategory() {
        when(preferenceRepository.findByUserIdAndType(USER_ID, NotificationType.HEARING_REMINDER))
                .thenReturn(Optional.empty());
        when(preferenceRepository.findByUserIdAndType(USER_ID, NotificationType.CASE_ASSIGNED))
                .thenReturn(Optional.empty());

        assertTrue(service.isEmailEnabledFor(USER_ID, NotificationType.HEARING_REMINDER));
        assertFalse(service.isEmailEnabledFor(USER_ID, NotificationType.CASE_ASSIGNED));
    }

    @Test
    @DisplayName("Stored row wins over default")
    void storedRow_wins() {
        NotificationPreference row = NotificationPreference.builder()
                .userId(USER_ID).type(NotificationType.CASE_ASSIGNED).emailEnabled(true).build();
        when(preferenceRepository.findByUserIdAndType(USER_ID, NotificationType.CASE_ASSIGNED))
                .thenReturn(Optional.of(row));

        assertTrue(service.isEmailEnabledFor(USER_ID, NotificationType.CASE_ASSIGNED));
    }

    @Test
    @DisplayName("Opting OUT of an ALERT type is rejected — deadlines must not be silencable")
    void alertOptOut_rejected() {
        UpsertNotificationPreferenceRequest req = new UpsertNotificationPreferenceRequest();
        req.setType(NotificationType.HEARING_REMINDER);
        req.setEmailEnabled(false);

        assertThrows(BusinessRuleException.class, () -> service.upsertMyPreference(req));
        verify(preferenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Opting OUT of a SYSTEM type saves the row")
    void systemOptOut_saves() {
        UpsertNotificationPreferenceRequest req = new UpsertNotificationPreferenceRequest();
        req.setType(NotificationType.CASE_ASSIGNED);
        req.setEmailEnabled(false);
        when(preferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.upsertMyPreference(req);

        verify(preferenceRepository).save(any());
    }

    @Test
    @DisplayName("My preferences view lists all types with effective values and mutability")
    void myPreferences_listsAllTypes() {
        when(preferenceRepository.findByUserId(USER_ID)).thenReturn(List.of());

        var rows = service.getMyPreferences();

        assertEquals(NotificationType.values().length, rows.size());
        var hearing = rows.stream()
                .filter(r -> r.type() == NotificationType.HEARING_REMINDER).findFirst().orElseThrow();
        assertTrue(hearing.emailEnabled(), "ALERT default ON");
        assertTrue(hearing.locked(), "ALERT not mutable");
        var caseAssigned = rows.stream()
                .filter(r -> r.type() == NotificationType.CASE_ASSIGNED).findFirst().orElseThrow();
        assertFalse(caseAssigned.emailEnabled(), "SYSTEM default OFF");
        assertFalse(caseAssigned.locked(), "SYSTEM mutable");
    }

    @Test
    @DisplayName("Unauthenticated preference access is rejected")
    void unauthenticated_rejected() {
        when(currentUserResolver.getCurrentUserId()).thenReturn(null);

        assertThrows(UnauthorizedException.class, service::getMyPreferences);
    }
}
