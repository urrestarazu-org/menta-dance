package com.menta.billing.application.port.out;

import com.menta.billing.application.dto.PurchaseExceptionNotification;

/**
 * Out-port for delivering a purchase-exception notification to its two
 * recipients (buyer + operations), modeled on {@code
 * com.menta.auth.application.port.out.ActivationNotificationPort} (design
 * File Changes table, Phase C).
 */
public interface PurchaseExceptionNotificationPort {

    /**
     * Sends the notification. MUST propagate a {@code MailException} (or any
     * other send failure) unchanged so the outbox worker marks the row
     * {@code FAILED} and retries — never swallowed here.
     */
    void notify(PurchaseExceptionNotification notification);
}
