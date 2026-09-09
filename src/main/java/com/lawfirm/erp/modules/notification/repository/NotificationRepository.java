package com.lawfirm.erp.modules.notification.repository;

import com.lawfirm.erp.modules.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

    boolean existsByDedupKeyAndRecipientUserId(String dedupKey, UUID recipientUserId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :readAt " +
           "WHERE n.recipientUserId = :recipientUserId AND n.readAt IS NULL")
    int markAllAsRead(@Param("recipientUserId") UUID recipientUserId,
                      @Param("readAt") LocalDateTime readAt);
}
