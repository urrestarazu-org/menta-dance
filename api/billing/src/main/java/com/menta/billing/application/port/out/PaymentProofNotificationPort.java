package com.menta.billing.application.port.out;

import com.menta.billing.application.dto.PaymentProofNotification;

/**
 * Out-port for notifying operations that a bank-transfer payment proof was submitted (#31,
 * US-BILLING-003, design D2/C12).
 */
public interface PaymentProofNotificationPort {

    /**
     * Sends the notification. MUST propagate a {@code MailException} (or any other send failure)
     * unchanged, per design C12 step 6 — a failed notification fails the request so the owner
     * retries within their upload budget.
     */
    void notifyProofSubmitted(PaymentProofNotification notification);
}
