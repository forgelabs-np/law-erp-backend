package com.lawfirm.erp.modules.notification.enums;

/**
 * Lifecycle of one out-of-band (email) dispatch:
 * PENDING → (scheduler picks up) → SENT
 *                                → FAILED → RETRYING (backoff) → … → DEAD
 */
public enum DeliveryStatus {
    PENDING,
    SENT,
    FAILED,
    RETRYING,
    DEAD
}
