package com.lawfirm.erp.modules.scraper.service;

// The only dependency the matching path has on delivery — a slow/failing channel never
// blocks matching. Throw only on genuine delivery failure so the row stays un-notified.
public interface NotificationDispatcher {

    String channel();

    void dispatch(String recipient, String subject, String body);
}
