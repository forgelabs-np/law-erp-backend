package com.lawfirm.erp.modules.notification.dispatcher;

import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.modules.email.service.EmailService;
import com.lawfirm.erp.modules.notification.entity.Notification;
import com.lawfirm.erp.modules.notification.entity.NotificationDelivery;
import com.lawfirm.erp.modules.notification.enums.DeliveryChannel;
import com.lawfirm.erp.modules.notification.enums.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Sends notification emails through the platform's email module (same SMTP
 * resolution chain as every other email). Never throws: failure is recorded
 * on the delivery row for the retry sweep.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationDispatcher implements NotificationDispatcher {

    private final EmailService emailService;
    private final UserRepository userRepository;

    @Override
    public DeliveryChannel channel() {
        return DeliveryChannel.EMAIL;
    }

    @Override
    public NotificationDelivery dispatch(Notification notification, NotificationDelivery delivery) {
        try {
            User recipient = userRepository.findById(delivery.getRecipientUserId()).orElse(null);
            if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
                return fail(delivery, "Recipient user or email missing");
            }

            boolean ok = emailService.sendNotificationEmail(
                    notification.getFirmId(), recipient.getId(), recipient.getEmail(),
                    notification.getTitle(), notification);

            if (ok) {
                delivery.setStatus(DeliveryStatus.SENT);
                delivery.setDeliveredAt(LocalDateTime.now());
                delivery.setErrorMessage(null);
            } else {
                delivery = fail(delivery, "SMTP handoff failed (see email module audit log)");
            }
        } catch (Exception e) {
            log.error("Email dispatch failed for notification {}: {}",
                    notification.getId(), e.getMessage());
            delivery = fail(delivery, e.getMessage());
        }
        return delivery;
    }

    private NotificationDelivery fail(NotificationDelivery delivery, String message) {
        delivery.setStatus(DeliveryStatus.FAILED);
        delivery.setErrorMessage(message != null && message.length() > 500
                ? message.substring(0, 500) : message);
        return delivery;
    }
}
