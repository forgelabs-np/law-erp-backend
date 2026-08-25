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

    /**
     * T-1 hearing reminder (scheduler-generated). reminderLogId is the
     * hearing_reminder_log row claimed before dispatch — flipped to FAILED if
     * the send fails, keeping the job idempotent and observable.
     */
    void sendHearingReminder(UUID firmId, UUID recipientUserId, String toEmail, String fullName,
                             com.lawfirm.erp.modules.email.dto.HearingReminderDetails details,
                             com.lawfirm.erp.modules.casemanagement.entity.HearingReminderLog.RecipientType recipientType,
                             UUID reminderLogId);
}
