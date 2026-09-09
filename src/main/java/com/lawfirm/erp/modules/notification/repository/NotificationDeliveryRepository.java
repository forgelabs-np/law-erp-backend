package com.lawfirm.erp.modules.notification.repository;

import com.lawfirm.erp.modules.notification.entity.NotificationDelivery;
import com.lawfirm.erp.modules.notification.enums.DeliveryChannel;
import com.lawfirm.erp.modules.notification.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    /** Rows the retry sweep may pick up now. */
    List<NotificationDelivery> findByStatusAndNextAttemptAtBefore(DeliveryStatus status, LocalDateTime cutoff);

    boolean existsByNotificationIdAndChannel(UUID notificationId, DeliveryChannel channel);

    List<NotificationDelivery> findByNotificationId(UUID notificationId);
}
