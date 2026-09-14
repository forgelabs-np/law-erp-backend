package com.lawfirm.erp.modules.notification.service;

import com.lawfirm.erp.modules.notification.dispatcher.NotificationDispatcher;
import com.lawfirm.erp.modules.notification.entity.Notification;
import com.lawfirm.erp.modules.notification.entity.NotificationDelivery;
import com.lawfirm.erp.modules.notification.enums.DeliveryStatus;
import com.lawfirm.erp.modules.notification.repository.NotificationDeliveryRepository;
import com.lawfirm.erp.modules.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every minute: picks up PENDING/RETRYING deliveries that are due and
 * hands each to its channel's dispatcher. SENT is terminal; FAILED gets
 * exponential backoff (1m → 5m → 30m) until the attempt cap (3), then
 * DEAD. One row's failure never stops the sweep.
 */
@Component
@Slf4j
public class NotificationRetryScheduler {

    static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MINUTES = {1, 5, 30};

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationRepository notificationRepository;
    private final Map<com.lawfirm.erp.modules.notification.enums.DeliveryChannel, NotificationDispatcher> dispatchers;

    public NotificationRetryScheduler(NotificationDeliveryRepository deliveryRepository,
                                      NotificationRepository notificationRepository,
                                      List<NotificationDispatcher> dispatcherList) {
        this.deliveryRepository = deliveryRepository;
        this.notificationRepository = notificationRepository;
        this.dispatchers = dispatcherList.stream()
                .collect(Collectors.toUnmodifiableMap(NotificationDispatcher::channel, Function.identity()));
    }

    @Scheduled(fixedDelay = 60_000)
    public void retryFailedDispatches() {
        LocalDateTime now = LocalDateTime.now();
        List<NotificationDelivery> due = new java.util.ArrayList<>();
        due.addAll(deliveryRepository.findByStatusAndNextAttemptAtBefore(DeliveryStatus.PENDING, now));
        due.addAll(deliveryRepository.findByStatusAndNextAttemptAtBefore(DeliveryStatus.RETRYING, now));
        if (due.isEmpty()) return;

        log.info("Notification retry sweep: {} delivery row(s) due", due.size());

        for (NotificationDelivery delivery : due) {
            try {
                processOne(delivery);
            } catch (Exception e) {
                log.error("Delivery {} sweep step failed: {}", delivery.getId(), e.getMessage(), e);
            }
        }
    }

    private void processOne(NotificationDelivery delivery) {
        NotificationDispatcher dispatcher = dispatchers.get(delivery.getChannel());
        if (dispatcher == null) {
            // No implementation for this channel yet — dead-letter rather than loop forever.
            delivery.setStatus(DeliveryStatus.DEAD);
            delivery.setErrorMessage("No dispatcher registered for channel " + delivery.getChannel());
            deliveryRepository.save(delivery);
            return;
        }

        Notification notification = notificationRepository.findById(delivery.getNotificationId())
                .orElse(null);
        if (notification == null) {
            // Parent notification gone (shouldn't happen) — dead-letter rather than loop.
            delivery.setStatus(DeliveryStatus.DEAD);
            delivery.setErrorMessage("Parent notification no longer exists");
            deliveryRepository.save(delivery);
            return;
        }

        try {
            NotificationDelivery result = dispatcher.dispatch(notification, delivery);
            applyResult(delivery, result);
        } catch (Exception e) {
            delivery.setStatus(DeliveryStatus.FAILED);
            delivery.setErrorMessage(e.getMessage());
            applyResult(delivery, delivery);
        }
        deliveryRepository.save(delivery);
        log.debug("Delivery {} → {} (attempt {})", delivery.getId(), delivery.getStatus(), delivery.getAttempts());
    }

    private void applyResult(NotificationDelivery delivery, NotificationDelivery result) {
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setLastAttemptedAt(LocalDateTime.now());
        delivery.setDeliveredAt(result.getDeliveredAt());
        delivery.setErrorMessage(result.getErrorMessage());

        if (result.getStatus() == DeliveryStatus.SENT) {
            delivery.setStatus(DeliveryStatus.SENT);
            delivery.setNextAttemptAt(null);
            return;
        }
        if (delivery.getAttempts() >= MAX_ATTEMPTS) {
            delivery.setStatus(DeliveryStatus.DEAD);
            delivery.setNextAttemptAt(null);
            return;
        }
        delivery.setStatus(DeliveryStatus.RETRYING);
        long backoff = BACKOFF_MINUTES[Math.min(delivery.getAttempts() - 1, BACKOFF_MINUTES.length - 1)];
        delivery.setNextAttemptAt(LocalDateTime.now().plusMinutes(backoff));
    }
}
