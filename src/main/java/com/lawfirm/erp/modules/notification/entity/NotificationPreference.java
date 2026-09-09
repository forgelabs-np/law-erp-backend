package com.lawfirm.erp.modules.notification.entity;

import com.lawfirm.erp.entity.base.AuditableEntity;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Per-user, per-type opt in/out of the EMAIL channel. Absent row = default
 * (see NotificationPreferenceServiceImpl): ALERT types are emailed and
 * NON-MUTABLE (a user must not silently miss an appeal deadline);
 * SYSTEM/BROADCAST default to in-app only, mutable.
 */
@Entity
@Table(name = "notification_preferences",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "type"}))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationPreference extends AuditableEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;
}
