package com.lawfirm.erp.modules.email.service;

import java.util.UUID;

public interface EmailService {

    void sendWelcomeEmployee(UUID firmId, UUID triggeredByUserId, String toEmail, String fullName,
                             String username, String tempPassword, String firmName, String firmCode);

    void sendWelcomeClient(UUID firmId, UUID triggeredByUserId, String toEmail, String fullName,
                           String username, String tempPassword, String firmName);

    void sendWelcomeFirmAdmin(UUID firmId, UUID triggeredByUserId, String toEmail, String fullName,
                              String username, String tempPassword, String firmName, String firmCode);

    void sendPasswordReset(UUID firmId, UUID triggeredByUserId, String toEmail, String fullName,
                           String tempPassword, String firmName);

    void sendPasswordResetLink(UUID firmId, UUID triggeredByUserId, String toEmail, String fullName,
                               String resetToken, int validMinutes, String firmName);

    void sendHearingReminder(UUID firmId, UUID recipientUserId, String toEmail, String fullName,
                             com.lawfirm.erp.modules.email.dto.HearingReminderDetails details,
                             com.lawfirm.erp.modules.casemanagement.entity.HearingReminderLog.RecipientType recipientType,
                             UUID reminderLogId);

    void sendInvoiceEmail(UUID firmId, UUID recipientUserId, String toEmail,
                          String firmName, String invoiceNumber,
                          java.math.BigDecimal total, byte[] pdfBytes);

    boolean sendNotificationEmail(UUID firmId, UUID recipientUserId, String toEmail,
                                  String subject,
                                  com.lawfirm.erp.modules.notification.entity.Notification notification);
}
