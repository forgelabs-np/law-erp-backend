package com.lawfirm.erp.modules.notification.dto;

import com.lawfirm.erp.modules.notification.enums.NotificationType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpsertNotificationPreferenceRequest {
    @NotNull(message = "Type is required")
    private NotificationType type;

    @NotNull(message = "emailEnabled is required")
    private Boolean emailEnabled;
}
