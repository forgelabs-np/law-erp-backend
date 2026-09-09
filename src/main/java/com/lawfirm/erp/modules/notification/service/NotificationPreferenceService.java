package com.lawfirm.erp.modules.notification.service;

import com.lawfirm.erp.modules.notification.dto.UpsertNotificationPreferenceRequest;
import com.lawfirm.erp.modules.notification.enums.NotificationType;

import java.util.List;
import java.util.UUID;

public interface NotificationPreferenceService {

    /** Effective email-channel setting for one user/type. Absent row = category default. */
    boolean isEmailEnabledFor(UUID userId, NotificationType type);

    /** Full view for the preferences UI: every type with effective value + mutability flag. */
    List<NotificationPreferenceView> getMyPreferences();

    /** Updates one preference; ALERT types reject opt-out with BusinessRuleException. */
    void upsertMyPreference(UpsertNotificationPreferenceRequest request);

    record NotificationPreferenceView(NotificationType type, boolean emailEnabled, boolean locked) {
    }
}
