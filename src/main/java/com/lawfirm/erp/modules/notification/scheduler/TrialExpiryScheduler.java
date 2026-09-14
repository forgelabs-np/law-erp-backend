package com.lawfirm.erp.modules.notification.scheduler;

import com.lawfirm.erp.common.constant.RoleCode;
import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import com.lawfirm.erp.modules.notification.event.NotificationEvent;
import com.lawfirm.erp.modules.notification.service.NotificationOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Runs daily at 09:00 to check trial firms:
 * - Sends TRIAL_EXPIRING (3 days before expiry) to firm admins
 * - Sends TRIAL_EXPIRED (on expiry day) to firm admins
 * - Sets expired firms to SUSPENDED status
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrialExpiryScheduler {

    private final FirmRepository firmRepository;
    private final NotificationOrchestrator notificationOrchestrator;

    @Scheduled(cron = "0 0 9 * * *") // daily at 09:00
    public void checkTrialExpiries() {
        List<Firm> trialFirms = firmRepository.findByIsTrialTrue();
        LocalDateTime now = LocalDateTime.now();

        for (Firm firm : trialFirms) {
            if (firm.getTrialExpiresAt() == null) continue;

            long daysRemaining = ChronoUnit.DAYS.between(now, firm.getTrialExpiresAt());

            if (daysRemaining < 0) {
                // Trial has expired — suspend firm
                handleExpiredTrial(firm);
            } else if (daysRemaining <= 3) {
                // Trial expiring soon — send warning
                handleExpiringTrial(firm, (int) daysRemaining);
            }
        }
    }

    private void handleExpiringTrial(Firm firm, int daysRemaining) {
        String dedupKey = "TRIAL_EXPIRING:" + firm.getId() + ":day" + daysRemaining;
        sendToAllFirmAdmins(firm, NotificationType.TRIAL_EXPIRING, dedupKey,
                Map.of("firmName", firm.getName(), "daysRemaining", String.valueOf(daysRemaining)));
        log.info("Trial expiring notification sent for firm {} ({} days remaining)", firm.getLawFirmCode(), daysRemaining);
    }

    private void handleExpiredTrial(Firm firm) {
        // Suspend the firm
        if (firm.getStatus() != FirmStatus.SUSPENDED) {
            firm.setStatus(FirmStatus.SUSPENDED);
            firmRepository.save(firm);
            log.info("Firm {} auto-suspended due to trial expiry", firm.getLawFirmCode());
        }

        String dedupKey = "TRIAL_EXPIRED:" + firm.getId();
        sendToAllFirmAdmins(firm, NotificationType.TRIAL_EXPIRED, dedupKey,
                Map.of("firmName", firm.getName()));
        log.info("Trial expired notification sent for firm {}", firm.getLawFirmCode());
    }

    private void sendToAllFirmAdmins(Firm firm, NotificationType type, String dedupKey,
                                      Map<String, Object> variables) {
        NotificationEvent event = new NotificationEvent(
                firm.getId(),
                null,               // not to a specific user
                RoleCode.FIRM_ADMIN,  // fan-out to all FIRM_ADMIN users in the firm
                false,
                type,
                "FIRM",
                firm.getId(),
                dedupKey,
                variables
        );
        notificationOrchestrator.process(event);
    }
}
