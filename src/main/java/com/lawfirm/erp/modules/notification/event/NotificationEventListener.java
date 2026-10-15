package com.lawfirm.erp.modules.notification.event;

import com.lawfirm.erp.modules.notification.service.NotificationOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationOrchestrator orchestrator;

    @Async("taskExecutor")
    public void onNotificationEvent(NotificationEvent event) {
        try {
            orchestrator.process(event);
        } catch (Exception e) {
            // A notification must never break the producer's business flow.
            log.error("Failed to process NotificationEvent type={} firm={}: {}",
                    event.type(), event.firmId(), e.getMessage(), e);
        }
    }
}
