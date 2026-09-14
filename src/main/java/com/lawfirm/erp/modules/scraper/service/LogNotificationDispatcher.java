package com.lawfirm.erp.modules.scraper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Stub channel; swap for a real email/SMS/webhook dispatcher behind the interface.
@Slf4j
@Component
public class LogNotificationDispatcher implements NotificationDispatcher {

    @Override
    public String channel() {
        return "log";
    }

    @Override
    public void dispatch(String recipient, String subject, String body) {
        log.info("[scraper-notify:{}] to={} | {} | {}", channel(), recipient, subject, body);
    }
}
