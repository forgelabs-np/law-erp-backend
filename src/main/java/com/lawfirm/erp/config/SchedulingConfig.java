package com.lawfirm.erp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Scheduled processing. This activates the scraper's daily/weekly jobs and also
 * the case-management module's lapsed-appeal watcher (AppealDeadlineEngine), which was
 * already annotated @Scheduled but never ran because @EnableScheduling was missing.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
