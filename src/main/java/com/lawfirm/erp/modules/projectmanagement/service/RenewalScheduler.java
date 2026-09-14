package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.modules.projectmanagement.repository.RenewalInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Scheduled job that runs daily to mark overdue renewal instances.
 * PENDING instances with dueDate < today are marked OVERDUE.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RenewalScheduler {

    private final RenewalInstanceRepository instanceRepository;

    @Scheduled(cron = "0 0 8 * * *") // Every day at 8:00 AM
    @Transactional
    public void markOverdueInstances() {
        LocalDate today = LocalDate.now();
        int marked = instanceRepository.markOverdueInstances(today);
        if (marked > 0) {
            log.info("Renewal scheduler: marked {} instances as OVERDUE (due before {})", marked, today);
        }
    }
}
