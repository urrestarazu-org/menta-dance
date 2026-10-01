package com.menta.billing.application.port.out;

import com.menta.billing.application.dto.SubscriptionFulfillmentAlarm;

/**
 * Out-port that raises an operator alarm when a settled virtual payment ends without subscription
 * access (#236). Not a notification to the buyer: no mail, no outbox event.
 */
public interface SubscriptionFulfillmentAlarmPort {

    /** Raises the alarm. Implementations must not throw: the caller's transaction must hold. */
    void raise(SubscriptionFulfillmentAlarm alarm);
}
