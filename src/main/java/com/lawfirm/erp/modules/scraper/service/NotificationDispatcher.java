package com.lawfirm.erp.modules.scraper.service;

public interface NotificationDispatcher {

    String channel();

    void dispatch(String recipient, String subject, String body);
}
