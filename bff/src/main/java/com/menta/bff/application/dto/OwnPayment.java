package com.menta.bff.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One of the student's own payments as {@code GET /api/v1/billing/payments/me} reports it (#363),
 * trimmed to what the history page shows. The API never sends the proof, the provider's data or an
 * external reference, and the BFF reads none of the identifiers either.
 *
 * @param status    where the payment stands
 * @param amount    amount paid
 * @param currency  ISO 4217 currency code of the amount
 * @param method    how it is paid
 * @param createdAt instant on which the payment was created
 * @param itemType  what was bought
 * @param itemName  plan name for a subscription; always {@code null} for a physical course, and
 *                  possibly {@code null} for any item
 */
public record OwnPayment(
        Status status, BigDecimal amount, String currency, Method method, Instant createdAt,
        ItemType itemType, String itemName) {

    /** Payment states. {@code UNKNOWN} stands for a value this BFF does not know yet. */
    public enum Status {
        AWAITING_PROVIDER,
        AWAITING_MANUAL_VERIFICATION,
        RECONCILIATION_REQUIRED,
        COMPLETED,
        REJECTED,
        CANCELLED,
        EXPIRED,
        UNKNOWN
    }

    /** Payment methods. {@code UNKNOWN} stands for a value this BFF does not know yet. */
    public enum Method {
        MERCADO_PAGO,
        BANK_TRANSFER,
        UNKNOWN
    }

    /** What a payment buys. {@code UNKNOWN} stands for a value this BFF does not know yet. */
    public enum ItemType {
        SUBSCRIPTION,
        PHYSICAL_COURSE,
        UNKNOWN
    }
}
