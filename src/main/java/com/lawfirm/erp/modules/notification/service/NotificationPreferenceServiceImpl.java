package com.lawfirm.erp.modules.notification.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.UnauthorizedException;
import com.lawfirm.erp.modules.notification.dto.UpsertNotificationPreferenceRequest;
import com.lawfirm.erp.modules.notification.entity.NotificationPreference;
import com.lawfirm.erp.modules.notification.enums.NotificationCategory;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import com.lawfirm.erp.modules.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final CurrentUserResolver currentUserResolver;

    @Override
    public boolean isEmailEnabledFor(UUID userId, NotificationType type) {
        return preferenceRepository.findByUserIdAndType(userId, type)
                .map(NotificationPreference::isEmailEnabled)
                .orElseGet(() -> type.getCategory() == NotificationCategory.ALERT);
    }

    @Override
    public List<NotificationPreferenceView> getMyPreferences() {
        UUID userId = requireCurrentUser();
        List<NotificationPreference> stored = preferenceRepository.findByUserId(userId);

        List<NotificationPreferenceView> views = new ArrayList<>();
        for (NotificationType type : NotificationType.values()) {
            boolean effective = stored.stream()
                    .filter(p -> p.getType() == type)
                    .findFirst()
                    .map(NotificationPreference::isEmailEnabled)
                    .orElseGet(() -> type.getCategory() == NotificationCategory.ALERT);
            boolean locked = type.getCategory() == NotificationCategory.ALERT;
            views.add(new NotificationPreferenceView(type, effective, locked));
        }
        return views;
    }

    @Override
    @Transactional
    public void upsertMyPreference(UpsertNotificationPreferenceRequest request) {
        UUID userId = requireCurrentUser();

        if (request.getType().getCategory() == NotificationCategory.ALERT && !request.getEmailEnabled()) {
            throw new BusinessRuleException(
                    "Deadlines and hearing reminders cannot be silenced — email stays on for "
                            + request.getType());
        }

        NotificationPreference pref = preferenceRepository.findByUserIdAndType(userId, request.getType())
                .orElseGet(() -> NotificationPreference.builder()
                        .userId(userId)
                        .type(request.getType())
                        .build());
        pref.setEmailEnabled(request.getEmailEnabled());
        preferenceRepository.save(pref);
    }

    private UUID requireCurrentUser() {
        UUID userId = currentUserResolver.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return userId;
    }
}
