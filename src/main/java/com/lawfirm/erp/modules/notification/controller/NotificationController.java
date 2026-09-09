package com.lawfirm.erp.modules.notification.controller;

import com.lawfirm.erp.common.constant.NotificationConstants;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.notification.dto.NotificationResponse;
import com.lawfirm.erp.modules.notification.dto.SendBroadcastRequest;
import com.lawfirm.erp.modules.notification.dto.UpsertNotificationPreferenceRequest;
import com.lawfirm.erp.modules.notification.service.BroadcastService;
import com.lawfirm.erp.modules.notification.service.NotificationPreferenceService;
import com.lawfirm.erp.modules.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications — bell icon feed, unread badge, read state")
public class NotificationController {

    private final NotificationService notificationService;
    private final BroadcastService broadcastService;
    private final NotificationPreferenceService preferenceService;
    private final ResponseHandler responseHandler;

    @GetMapping
    @Operation(summary = NotificationConstants.LIST_NOTIFICATIONS_SUMMARY,
               description = NotificationConstants.LIST_NOTIFICATIONS_DESCRIPTION)
    public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responseHandler.ok(
                notificationService.getMyNotifications(unreadOnly, page, size),
                "Notifications fetched successfully");
    }

    @GetMapping("/unread-count")
    @Operation(summary = NotificationConstants.UNREAD_COUNT_SUMMARY,
               description = NotificationConstants.UNREAD_COUNT_DESCRIPTION)
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        return responseHandler.ok(
                Map.of("count", notificationService.getUnreadCount()),
                "Unread count fetched successfully");
    }

    @PostMapping("/{notificationId}/read")
    @Operation(summary = NotificationConstants.MARK_READ_SUMMARY)
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable UUID notificationId) {
        notificationService.markAsRead(notificationId);
        return responseHandler.ok(null, "Notification marked as read");
    }

    @PostMapping("/read-all")
    @Operation(summary = NotificationConstants.MARK_ALL_READ_SUMMARY)
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead() {
        int updated = notificationService.markAllAsRead();
        return responseHandler.ok(Map.of("updated", updated), "All notifications marked as read");
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('FIRM_ADMIN')")
    @Operation(summary = NotificationConstants.SEND_BROADCAST_SUMMARY,
               description = NotificationConstants.SEND_BROADCAST_DESCRIPTION)
    public ResponseEntity<ApiResponse<Void>> broadcast(
            @Valid @RequestBody ApiRequest<SendBroadcastRequest> request) {
        broadcastService.send(request.getData());
        return responseHandler.ok(null, "Announcement sent successfully");
    }

    @GetMapping("/preferences")
    @Operation(summary = NotificationConstants.GET_PREFERENCES_SUMMARY,
               description = NotificationConstants.GET_PREFERENCES_DESCRIPTION)
    public ResponseEntity<ApiResponse<List<NotificationPreferenceService.NotificationPreferenceView>>> preferences() {
        return responseHandler.ok(preferenceService.getMyPreferences(),
                "Notification preferences fetched successfully");
    }

    @PutMapping("/preferences")
    @Operation(summary = NotificationConstants.UPSERT_PREFERENCE_SUMMARY)
    public ResponseEntity<ApiResponse<Void>> updatePreference(
            @Valid @RequestBody ApiRequest<UpsertNotificationPreferenceRequest> request) {
        preferenceService.upsertMyPreference(request.getData());
        return responseHandler.ok(null, "Notification preference updated successfully");
    }
}
