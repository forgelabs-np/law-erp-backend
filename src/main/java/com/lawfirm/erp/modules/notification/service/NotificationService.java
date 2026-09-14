package com.lawfirm.erp.modules.notification.service;

import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.modules.notification.dto.NotificationResponse;

import java.util.UUID;

public interface NotificationService {

    PagedResponse<NotificationResponse> getMyNotifications(boolean unreadOnly, int page, int size);

    long getUnreadCount();

    void markAsRead(UUID notificationId);

    int markAllAsRead();
}
