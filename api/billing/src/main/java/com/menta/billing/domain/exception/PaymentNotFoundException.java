package com.menta.billing.domain.exception;

import com.menta.billing.domain.model.PaymentId;
import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown by Billing application code when a {@code paymentId} resolves to
 * no row in {@code billing_payments} / {@code billing_purchases} — a use
 * error (the production path never hits this condition; the V8 UNIQUE
 * constraint prevents the "ghost payment" anti-pattern).
 */
public class PaymentNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "PAYMENT_NOT_FOUND";

    public PaymentNotFoundException(PaymentId paymentId) {
        super(ERROR_CODE, "No payment found for paymentId=" + paymentId.getValue());
    }
}
