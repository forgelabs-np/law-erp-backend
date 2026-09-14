package com.lawfirm.erp.modules.notification.service;

import com.lawfirm.erp.common.constant.NotificationConstants;
import com.lawfirm.erp.modules.notification.event.NotificationEvent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Composes notification copy per type at code level (v1) — DB-backed
 * templates are deferred until i18n needs them. Each recipient renders
 * independently so future per-recipient variables stay possible.
 */
@Component
public class NotificationRenderer {

    public RenderedNotification render(NotificationEvent event, UUID recipientUserId) {
        return switch (event.type()) {
            case CASE_ASSIGNED -> renderCaseAssigned(event);
            case INVOICE_STATUS -> renderInvoiceStatus(event);
            case HEARING_REMINDER -> renderHearingReminder(event);
            case APPEAL_DEADLINE -> renderAppealDeadline(event);
            case APPEAL_LAPSED -> renderAppealLapsed(event);
            case TRIAL_EXPIRING -> renderTrialExpiring(event);
            case TRIAL_EXPIRED -> renderTrialExpired(event);
            case ANNOUNCEMENT -> renderAnnouncement(event);
        };
    }

    private RenderedNotification renderCaseAssigned(NotificationEvent event) {
        String matterNumber = str(event.variables(), "matterNumber");
        String role = str(event.variables(), "assignmentRole");
        return new RenderedNotification(
                NotificationConstants.CASE_ASSIGNED_TITLE,
                String.format(NotificationConstants.CASE_ASSIGNED_BODY, matterNumber, role));
    }

    private RenderedNotification renderInvoiceStatus(NotificationEvent event) {
        String invoiceNumber = str(event.variables(), "invoiceNumber");
        String status = str(event.variables(), "status");
        return new RenderedNotification(
                String.format(NotificationConstants.INVOICE_STATUS_TITLE, invoiceNumber),
                String.format(NotificationConstants.INVOICE_STATUS_BODY, invoiceNumber, status));
    }

    private RenderedNotification renderHearingReminder(NotificationEvent event) {
        String matterNumber = str(event.variables(), "matterNumber");
        String courtName = str(event.variables(), "courtName");
        String time = str(event.variables(), "hearingTime");
        return new RenderedNotification(
                NotificationConstants.HEARING_REMINDER_TITLE,
                String.format(NotificationConstants.HEARING_REMINDER_BODY, matterNumber, courtName, time));
    }

    private RenderedNotification renderAppealDeadline(NotificationEvent event) {
        String matterNumber = str(event.variables(), "matterNumber");
        String courtCaseRef = str(event.variables(), "courtCaseRef");
        String deadline = str(event.variables(), "deadline");
        String days = str(event.variables(), "daysRemaining");
        return new RenderedNotification(
                NotificationConstants.APPEAL_DEADLINE_TITLE,
                String.format(NotificationConstants.APPEAL_DEADLINE_BODY,
                        matterNumber, courtCaseRef, deadline, days));
    }

    private RenderedNotification renderAppealLapsed(NotificationEvent event) {
        String matterNumber = str(event.variables(), "matterNumber");
        String courtCaseRef = str(event.variables(), "courtCaseRef");
        return new RenderedNotification(
                NotificationConstants.APPEAL_LAPSED_TITLE,
                String.format(NotificationConstants.APPEAL_LAPSED_BODY, matterNumber, courtCaseRef));
    }

    private RenderedNotification renderTrialExpiring(NotificationEvent event) {
        String firmName = str(event.variables(), "firmName");
        String daysRemaining = str(event.variables(), "daysRemaining");
        int days;
        try {
            days = Integer.parseInt(daysRemaining);
        } catch (NumberFormatException e) {
            days = 0;
        }
        return new RenderedNotification(
                NotificationConstants.TRIAL_EXPIRING_TITLE,
                String.format(NotificationConstants.TRIAL_EXPIRING_BODY, firmName, days));
    }

    private RenderedNotification renderTrialExpired(NotificationEvent event) {
        String firmName = str(event.variables(), "firmName");
        return new RenderedNotification(
                NotificationConstants.TRIAL_EXPIRED_TITLE,
                String.format(NotificationConstants.TRIAL_EXPIRED_BODY, firmName));
    }

    private RenderedNotification renderAnnouncement(NotificationEvent event) {
        String title = str(event.variables(), "title");
        String body = str(event.variables(), "body");
        return new RenderedNotification(
                String.format(NotificationConstants.ANNOUNCEMENT_TITLE, title),
                String.format(NotificationConstants.ANNOUNCEMENT_BODY, body));
    }

    private String str(Map<String, Object> vars, String key) {
        Object v = vars.get(key);
        return v != null ? String.valueOf(v) : "unknown";
    }
}
