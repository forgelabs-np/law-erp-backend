package com.lawfirm.erp.modules.casemanagement.service;

import java.time.LocalDate;

public interface HearingReminderService {

    /**
     * Sends T-1 hearing reminders for all PESHI/SCHEDULED events on {@code date}
     * to the attending advocate and every linked our-client. Idempotent per
     * (event, recipient, date) via the hearing_reminder_log unique key.
     *
     * @return number of reminder emails dispatched
     */
    int sendRemindersForDate(LocalDate date);
}
