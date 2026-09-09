package com.lawfirm.erp.modules.notification.event;

import com.lawfirm.erp.modules.notification.enums.NotificationCategory;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationEventTest {

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REF_ID = UUID.randomUUID();

    @Test
    @DisplayName("toUser targets exactly one user, no fan-out flags set")
    void toUser_targetsSingleRecipient() {
        NotificationEvent event = NotificationEvent.toUser(FIRM_ID, USER_ID,
                NotificationType.CASE_ASSIGNED, "MATTER", REF_ID, Map.of("k", "v"));

        assertEquals(FIRM_ID, event.firmId());
        assertEquals(USER_ID, event.recipientUserId());
        assertNull(event.recipientRoleCode());
        assertFalse(event.allFirmUsers());
        assertEquals("MATTER", event.referenceType());
        assertEquals(REF_ID, event.referenceId());
    }

    @Test
    @DisplayName("toRole sets role code, no direct user")
    void toRole_setsRoleTargeting() {
        NotificationEvent event = NotificationEvent.toRole(FIRM_ID, "FIRM_ADMIN",
                NotificationType.INVOICE_STATUS, "INVOICE", REF_ID, Map.of());

        assertNull(event.recipientUserId());
        assertEquals("FIRM_ADMIN", event.recipientRoleCode());
        assertFalse(event.allFirmUsers());
    }

    @Test
    @DisplayName("Null variables map becomes empty immutable map")
    void nullVariables_becomeEmptyMap() {
        NotificationEvent event = new NotificationEvent(FIRM_ID, USER_ID, null, false,
                NotificationType.CASE_ASSIGNED, null, null, null, null);

        assertNotNull(event.variables());
        assertTrue(event.variables().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> event.variables().put("x", "y"), "variables are defensively immutable");
    }

    @Test
    @DisplayName("Each type pins exactly one category")
    void types_carryCategory() {
        assertEquals(NotificationCategory.SYSTEM, NotificationType.CASE_ASSIGNED.getCategory());
        assertEquals(NotificationCategory.SYSTEM, NotificationType.INVOICE_STATUS.getCategory());
        assertEquals(NotificationCategory.ALERT, NotificationType.HEARING_REMINDER.getCategory());
        assertEquals(NotificationCategory.BROADCAST, NotificationType.ANNOUNCEMENT.getCategory());
    }

    @Test
    @DisplayName("Variables are defensively copied and preserve value types")
    void variables_preserveValues() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("count", 3);

        NotificationEvent event = new NotificationEvent(FIRM_ID, USER_ID, null, false,
                NotificationType.CASE_ASSIGNED, null, null, null, vars);

        assertEquals(3, event.variables().get("count"), "renderer stringifies via String.valueOf");
        assertNotSame(vars, event.variables(), "map is defensively copied");
    }
}
