package com.lawfirm.erp.modules.notification.service;

/**
 * Title/body pair produced by NotificationRenderer for one recipient.
 * Kept as a plain record so rendering stays testable without persistence.
 */
public record RenderedNotification(String title, String body) {
}
