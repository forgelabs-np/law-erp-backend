package com.lawfirm.erp.modules.scraper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stub channel — logs the delivery instead of sending. Replace (or add alongside) with a
 * real email/SMS/webhook dispatcher; the matching path only knows the interface.
 */
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
