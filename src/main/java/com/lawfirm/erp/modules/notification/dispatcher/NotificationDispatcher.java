package com.lawfirm.erp.modules.notification.dispatcher;

import com.lawfirm.erp.modules.notification.entity.Notification;
import com.lawfirm.erp.modules.notification.entity.NotificationDelivery;
import com.lawfirm.erp.modules.notification.enums.DeliveryChannel;

/**
 * One implementation per fallible out-of-band channel. Implementations
 * must never throw — they report outcome via the returned delivery row
 * (SENT, or FAILED with errorMessage) so the orchestrator/sweep can
 * translate it into retry state. EMAIL needs the recipient's address,
 * so implementations resolve users themselves.
 */
public interface NotificationDispatcher {

    DeliveryChannel channel();

    /**
     * Attempts delivery of one notification to one user.
     * Must be side-effect-safe to call multiple times.
     */
    NotificationDelivery dispatch(Notification notification, NotificationDelivery delivery);
}
