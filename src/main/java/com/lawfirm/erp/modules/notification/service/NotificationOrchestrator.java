package com.lawfirm.erp.modules.notification.service;

import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.modules.notification.entity.Notification;
import com.lawfirm.erp.modules.notification.entity.NotificationDelivery;
import com.lawfirm.erp.modules.notification.enums.DeliveryChannel;
import com.lawfirm.erp.modules.notification.enums.DeliveryStatus;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import com.lawfirm.erp.modules.notification.event.NotificationEvent;
import com.lawfirm.erp.modules.notification.repository.NotificationDeliveryRepository;
import com.lawfirm.erp.modules.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core pipeline step: turns one NotificationEvent into one persisted
 * Notification per recipient (the in-app channel), plus PENDING
 * notification_delivery rows for fallible channels the recipient's
 * preferences ask for (EMAIL today — ALERT types default on, SYSTEM
 * default off, both overridable except ALERT opt-out which is locked).
 */
@Service
@Slf4j
public class NotificationOrchestrator {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationRenderer renderer;
    private final NotificationPreferenceService preferenceService;
    private final NotificationDeliveryRepository deliveryRepository;

    public NotificationOrchestrator(NotificationRepository notificationRepository,
                                    UserRepository userRepository,
                                    NotificationRenderer renderer,
                                    NotificationPreferenceService preferenceService,
                                    NotificationDeliveryRepository deliveryRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.renderer = renderer;
        this.preferenceService = preferenceService;
        this.deliveryRepository = deliveryRepository;
    }

    public void process(NotificationEvent event) {
        List<UUID> recipientIds = resolveRecipients(event);
        if (recipientIds.isEmpty()) {
            log.debug("NotificationEvent {} had no recipients — nothing persisted", event.type());
            return;
        }

        List<Notification> notifications = new ArrayList<>(recipientIds.size());
        List<NotificationDelivery> deliveries = new ArrayList<>();

        for (UUID recipientId : recipientIds) {
            if (event.dedupKey() != null
                    && notificationRepository.existsByDedupKeyAndRecipientUserId(event.dedupKey(), recipientId)) {
                log.debug("Skipping duplicate notification {} for user {}", event.dedupKey(), recipientId);
                continue;
            }
            RenderedNotification rendered = renderer.render(event, recipientId);
            Notification saved = notificationRepository.save(buildNotification(event, recipientId, rendered));
            notifications.add(saved);

            if (shouldEmail(event.type(), recipientId)) {
                deliveries.add(NotificationDelivery.builder()
                        .firmId(event.firmId())
                        .notificationId(saved.getId())
                        .recipientUserId(recipientId)
                        .channel(DeliveryChannel.EMAIL)
                        .status(DeliveryStatus.PENDING)
                        .nextAttemptAt(LocalDateTime.now()) // due immediately — sweep runs every minute
                        .build());
            }
        }

        if (!deliveries.isEmpty()) {
            deliveryRepository.saveAll(deliveries);
        }
        log.info("Persisted {} notification(s) of type {} for firm {} ({} email(s) enqueued)",
                notifications.size(), event.type(), event.firmId(), deliveries.size());
    }

    private boolean shouldEmail(NotificationType type, UUID recipientId) {
        try {
            return preferenceService.isEmailEnabledFor(recipientId, type);
        } catch (Exception e) {
            log.warn("Preference lookup failed for user {} — defaulting type {} to no email: {}",
                    recipientId, type, e.getMessage());
            return false;
        }
    }

    private List<UUID> resolveRecipients(NotificationEvent event) {
        if (event.recipientUserId() != null) {
            return List.of(event.recipientUserId());
        }
        if (event.recipientRoleCode() != null) {
            return userRepository.findUserIdsByFirmIdAndRoleCode(event.firmId(), event.recipientRoleCode());
        }
        if (event.allFirmUsers()) {
            return userRepository.findUserIdsByFirmId(event.firmId());
        }
        throw new IllegalArgumentException(
                "NotificationEvent has no targeting mode (user, role, or allFirmUsers) — producer bug");
    }

    private Notification buildNotification(NotificationEvent event, UUID recipientId,
                                           RenderedNotification rendered) {
        return Notification.builder()
                .firmId(event.firmId())
                .recipientUserId(recipientId)
                .type(event.type())
                .category(event.type().getCategory())
                .title(rendered.title())
                .body(rendered.body())
                .referenceType(event.referenceType())
                .referenceId(event.referenceId())
                .dedupKey(event.dedupKey())
                .build();
    }
}
