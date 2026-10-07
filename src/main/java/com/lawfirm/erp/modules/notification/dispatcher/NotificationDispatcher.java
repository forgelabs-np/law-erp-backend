package com.lawfirm.erp.modules.notification.dispatcher;

import com.lawfirm.erp.modules.notification.entity.Notification;
import com.lawfirm.erp.modules.notification.entity.NotificationDelivery;
import com.lawfirm.erp.modules.notification.enums.DeliveryChannel;

public interface NotificationDispatcher {

    DeliveryChannel channel();

    NotificationDelivery dispatch(Notification notification, NotificationDelivery delivery);
}
