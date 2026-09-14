package com.lawfirm.erp.modules.casemanagement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Daily job (8:00 AM) that emails T-1 hearing reminders to the attending
 * advocate and linked clients — see HearingReminderServiceImpl.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HearingReminderScheduler {

    private final HearingReminderService hearingReminderService;

    @Scheduled(cron = "0 0 8 * * *") // Every day at 8:00 AM
    public void sendTomorrowReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        int sent = hearingReminderService.sendRemindersForDate(tomorrow);
        if (sent > 0) {
            log.info("Hearing reminder scheduler: sent {} reminder email(s) for hearings on {}", sent, tomorrow);
        }
    }
}
