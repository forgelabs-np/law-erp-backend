package com.lawfirm.erp.modules.notification.entity;

import com.lawfirm.erp.entity.base.AuditableEntity;
import com.lawfirm.erp.modules.notification.enums.DeliveryChannel;
import com.lawfirm.erp.modules.notification.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One out-of-band dispatch attempt record for one notification recipient.
 * Only fallible channels (EMAIL today) are tracked — the in-app channel's
 * record is the notification row itself. The retry sweep requeues
 * RETRYING rows with backoff until attempts are exhausted (DEAD).
 */
@Entity
@Table(name = "notification_deliveries", indexes = {
        @Index(name = "idx_notifdel_due", columnList = "status, nextAttemptAt"),
        @Index(name = "idx_notifdel_notification", columnList = "notificationId")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationDelivery extends AuditableEntity {

    @Column(name = "firm_id", nullable = false)
    private UUID firmId;

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    private LocalDateTime lastAttemptedAt;

    /** When the retry sweep may next pick this row up. */
    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    private LocalDateTime deliveredAt;

    @Column(columnDefinition = "text")
    private String errorMessage;
}
