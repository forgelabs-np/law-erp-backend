package com.lawfirm.erp.modules.notification.entity;

import com.lawfirm.erp.entity.base.AuditableEntity;
import com.lawfirm.erp.modules.notification.enums.NotificationCategory;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_recipient_read", columnList = "recipientUserId, readAt"),
        @Index(name = "idx_notif_firm", columnList = "firmId"),
        @Index(name = "idx_notif_dedup", columnList = "dedupKey, recipientUserId", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Notification extends AuditableEntity {

    @Column(name = "firm_id", nullable = false)
    private UUID firmId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "dedup_key", length = 200)
    private String dedupKey;

    public boolean isUnread() {
        return readAt == null;
    }

    public void markRead(LocalDateTime at) {
        if (readAt == null) {
            readAt = at;
        }
    }
}
