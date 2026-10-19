package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * The provider could not be asked to open a checkout (US-BILLING-010).
 *
 * <p>The whole checkout transaction rolls back with it, so no local {@code
 * Payment} or {@code Subscription} survives a failed preference call — a
 * dangling payment with no way for the buyer to pay it would be worse than
 * no payment at all. No automatic retry: US-BILLING-010's integrity NFR
 * forbids creating a new external charge on an uncertain result.</p>
 */
public class PaymentPreferenceUnavailableException extends BusinessException {

    private static final String ERROR_CODE = "PAYMENT_PREFERENCE_UNAVAILABLE";

    public PaymentPreferenceUnavailableException(Throwable cause) {
        super(ERROR_CODE, "Could not open a checkout with the payment provider", cause);
    }
}
