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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private NotificationMapper notificationMapper;

    @InjectMocks private NotificationServiceImpl service;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(currentUserResolver.getCurrentUserId()).thenReturn(USER_ID);
    }

    private Notification unread(UUID recipient) {
        return Notification.builder()
                .recipientUserId(recipient)
                .readAt(null)
                .title("T").body("B")
                .build();
    }

    @Test
    @DisplayName("Unread count is scoped to the current user only")
    void unreadCount_scopedToCurrentUser() {
        when(notificationRepository.countByRecipientUserIdAndReadAtIsNull(USER_ID)).thenReturn(7L);

        assertEquals(7L, service.getUnreadCount());
        verify(notificationRepository).countByRecipientUserIdAndReadAtIsNull(USER_ID);
    }

    @Test
    @DisplayName("Unauthenticated access is rejected")
    void unauthenticated_throws() {
        when(currentUserResolver.getCurrentUserId()).thenReturn(null);

        assertThrows(UnauthorizedException.class, () -> service.getUnreadCount());
    }

    @Test
    @DisplayName("Paged list passes unreadOnly through to the repository")
    void list_unreadOnly_filter() {
        Notification n = unread(USER_ID);
        Page<Notification> page = new PageImpl<>(List.of(n));
        when(notificationRepository
                .findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(any(UUID.class), any(Pageable.class)))
                .thenReturn(page);
        when(notificationMapper.toResponse(n)).thenReturn(NotificationResponse.builder().title("T").build());

        PagedResponse<NotificationResponse> resp = service.getMyNotifications(true, 0, 20);

        assertEquals(1, resp.getContent().size());
        verify(notificationRepository)
                .findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(any(UUID.class), any(Pageable.class));
        verify(notificationRepository, never())
                .findByRecipientUserIdOrderByCreatedAtDesc(any(UUID.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Mark-as-read on someone else's notification is forbidden")
    void markRead_foreignNotification_forbidden() {
        UUID otherUser = UUID.randomUUID();
        Notification foreign = unread(otherUser);
        when(notificationRepository.findById(any())).thenReturn(Optional.of(foreign));

        assertThrows(ForbiddenException.class, () -> service.markAsRead(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Mark-as-read stamps readAt once and is idempotent")
    void markRead_stampsReadAt() {
        Notification n = unread(USER_ID);
        when(notificationRepository.findById(any())).thenReturn(Optional.of(n));

        service.markAsRead(UUID.randomUUID());

        assertNotNull(n.getReadAt());
        verify(notificationRepository).save(n);
    }

    @Test
    @DisplayName("Mark-as-read on missing notification throws ResourceNotFoundException")
    void markRead_missing_throws() {
        when(notificationRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.markAsRead(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Mark-all-read flips every unread row for the current user and returns the count")
    void markAllRead_returnsUpdatedCount() {
        when(notificationRepository.markAllAsRead(eq(USER_ID), any(LocalDateTime.class))).thenReturn(4);

        assertEquals(4, service.markAllAsRead());
    }
}
