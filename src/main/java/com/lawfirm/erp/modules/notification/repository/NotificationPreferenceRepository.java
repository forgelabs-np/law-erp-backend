package com.lawfirm.erp.modules.notification.repository;

import com.lawfirm.erp.modules.notification.entity.NotificationPreference;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    Optional<NotificationPreference> findByUserIdAndType(UUID userId, NotificationType type);

    List<NotificationPreference> findByUserId(UUID userId);

    void deleteByUserIdAndType(UUID userId, NotificationType type);
}
