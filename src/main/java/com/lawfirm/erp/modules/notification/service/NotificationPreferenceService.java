package com.lawfirm.erp.modules.notification.service;

import com.lawfirm.erp.modules.notification.dto.UpsertNotificationPreferenceRequest;
import com.lawfirm.erp.modules.notification.enums.NotificationType;

import java.util.List;
import java.util.UUID;

public interface NotificationPreferenceService {

    boolean isEmailEnabledFor(UUID userId, NotificationType type);

    List<NotificationPreferenceView> getMyPreferences();

    void upsertMyPreference(UpsertNotificationPreferenceRequest request);

    record NotificationPreferenceView(NotificationType type, boolean emailEnabled, boolean locked) {
    }
}
