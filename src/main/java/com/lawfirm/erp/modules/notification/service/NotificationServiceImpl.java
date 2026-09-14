package com.lawfirm.erp.modules.notification.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.exception.UnauthorizedException;
import com.lawfirm.erp.modules.notification.dto.NotificationResponse;
import com.lawfirm.erp.modules.notification.entity.Notification;
import com.lawfirm.erp.modules.notification.mapper.NotificationMapper;
import com.lawfirm.erp.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final CurrentUserResolver currentUserResolver;
    private final NotificationMapper notificationMapper;

    @Override
    public PagedResponse<NotificationResponse> getMyNotifications(boolean unreadOnly, int page, int size) {
        UUID userId = requireCurrentUser();
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);

        Page<Notification> result = unreadOnly
                ? notificationRepository.findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable);

        return PagedResponse.of(result, result.getContent().stream()
                .map(notificationMapper::toResponse)
                .toList());
    }

    @Override
    public long getUnreadCount() {
        return notificationRepository.countByRecipientUserIdAndReadAtIsNull(requireCurrentUser());
    }

    @Override
    @Transactional
    public void markAsRead(UUID notificationId) {
        UUID userId = requireCurrentUser();
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        if (!notification.getRecipientUserId().equals(userId)) {
            throw new ForbiddenException("You can only manage your own notifications");
        }
        notification.markRead(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public int markAllAsRead() {
        UUID userId = requireCurrentUser();
        int updated = notificationRepository.markAllAsRead(userId, LocalDateTime.now());
        return updated;
    }

    private UUID requireCurrentUser() {
        UUID userId = currentUserResolver.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return userId;
    }
}
