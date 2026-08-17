package com.lawfirm.erp.modules.scraper.service;

/**
 * The notification channel for new hearing matches. The scrape/matching path depends only
 * on this interface — a slow or failing channel can never block ingestion or matching.
 */
public interface NotificationDispatcher {

    /** Human-readable channel name for logging/diagnostics. */
    String channel();

    /**
     * Deliver a notification. Implementations should return normally on success and throw
     * only on genuine delivery failure (the caller then leaves the row un-notified for retry).
     */
    void dispatch(String recipient, String subject, String body);
}
