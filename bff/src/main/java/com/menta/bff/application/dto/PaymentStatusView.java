package com.menta.bff.application.dto;

/**
 * What the return page needs about a payment.
 *
 * @param progress    where the payment stands
 * @param fulfillment purchase outcome, {@code null} when absent or unknown
 */
public record PaymentStatusView(PaymentProgress progress, PurchaseFulfillment fulfillment) {
}
