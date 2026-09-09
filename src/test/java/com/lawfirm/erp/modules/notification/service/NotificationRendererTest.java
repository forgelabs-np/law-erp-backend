package com.lawfirm.erp.modules.notification.service;

import com.lawfirm.erp.modules.notification.event.NotificationEvent;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationRendererTest {

    private final NotificationRenderer renderer = new NotificationRenderer();

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID MATTER_ID = UUID.randomUUID();

    @Test
    @DisplayName("CASE_ASSIGNED renders matter number and assignment role")
    void rendersCaseAssigned() {
        NotificationEvent event = NotificationEvent.toUser(FIRM_ID, UUID.randomUUID(),
                NotificationType.CASE_ASSIGNED, "MATTER", MATTER_ID,
                Map.of("matterNumber", "APX-MAT-2026-00007", "assignmentRole", "PRIMARY_ADVOCATE"));

        var rendered = renderer.render(event, UUID.randomUUID());

        assertEquals("Assigned to matter", rendered.title());
        assertTrue(rendered.body().contains("APX-MAT-2026-00007"), "body names the matter");
        assertTrue(rendered.body().contains("PRIMARY_ADVOCATE"), "body names the role");
    }

    @Test
    @DisplayName("INVOICE_STATUS renders invoice number and new status")
    void rendersInvoiceStatus() {
        NotificationEvent event = NotificationEvent.toRole(FIRM_ID, "FIRM_ADMIN",
                NotificationType.INVOICE_STATUS, "INVOICE", UUID.randomUUID(),
                Map.of("invoiceNumber", "INV-2026-0004", "status", "PAID"));

        var rendered = renderer.render(event, UUID.randomUUID());

        assertTrue(rendered.title().contains("INV-2026-0004"), "title names the invoice");
        assertTrue(rendered.body().contains("PAID"), "body names the status");
    }

    @Test
    @DisplayName("Missing variables render as 'unknown' instead of 'null' or throwing")
    void missingVariables_renderAsUnknown() {
        NotificationEvent event = NotificationEvent.toUser(FIRM_ID, UUID.randomUUID(),
                NotificationType.CASE_ASSIGNED, "MATTER", MATTER_ID, Map.of());

        var rendered = renderer.render(event, UUID.randomUUID());

        assertNotNull(rendered.title());
        assertNotNull(rendered.body());
        assertFalse(rendered.body().contains("null"));
        assertFalse(rendered.body().contains("@"));
    }

    @Test
    @DisplayName("ANNOUNCEMENT renders title and body verbatim from variables")
    void rendersAnnouncement() {
        NotificationEvent event = NotificationEvent.toUser(FIRM_ID, UUID.randomUUID(),
                NotificationType.ANNOUNCEMENT, "FIRM", UUID.randomUUID(),
                Map.of("title", "Firm closed on dashain", "body", "Back on the 16th."));

        var rendered = renderer.render(event, UUID.randomUUID());

        assertEquals("Firm closed on dashain", rendered.title());
        assertEquals("Back on the 16th.", rendered.body());
    }

    @Test
    @DisplayName("ALERT types render their copy (hearing reminder)")
    void rendersHearingReminder() {
        NotificationEvent event = NotificationEvent.toUser(FIRM_ID, UUID.randomUUID(),
                NotificationType.HEARING_REMINDER, "COURT_EVENT", UUID.randomUUID(),
                Map.of("matterNumber", "M-9", "courtName", "Kathmandu DC", "hearingTime", "10:30"));

        var rendered = renderer.render(event, UUID.randomUUID());

        assertTrue(rendered.title().contains("Hearing"));
        assertTrue(rendered.body().contains("M-9"));
        assertTrue(rendered.body().contains("Kathmandu DC"));
    }
}
