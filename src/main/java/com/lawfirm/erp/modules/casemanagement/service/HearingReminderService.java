package com.lawfirm.erp.modules.casemanagement.service;

import java.time.LocalDate;

public interface HearingReminderService {

    int sendRemindersForDate(LocalDate date);
}
