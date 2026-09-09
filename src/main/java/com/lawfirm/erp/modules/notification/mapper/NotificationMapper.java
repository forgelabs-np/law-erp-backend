package com.lawfirm.erp.modules.notification.mapper;

import com.lawfirm.erp.modules.notification.dto.NotificationResponse;
import com.lawfirm.erp.modules.notification.entity.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType() != null ? n.getType().name() : null)
                .category(n.getCategory() != null ? n.getCategory().name() : null)
                .title(n.getTitle())
                .body(n.getBody())
                .referenceType(n.getReferenceType())
                .referenceId(n.getReferenceId())
                .read(!n.isUnread())
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
