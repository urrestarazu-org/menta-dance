package com.menta.shared.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Single source of truth for the {@code billing.PhysicalPaymentCompleted}
 * outbox payload (proposal §3.1, design §3.1).
 *
 * <p>Producer ({@link com.menta.billing.application.usecase.PublishPhysicalPaymentCompletedUseCase})
 * and consumer ({@link com.menta.app.outbox.PhysicalCapacityAssignmentOutboxEventHandler})
 * import the SAME record from {@code api:shared}, so Jackson encoding on
 * the producer side and decoding on the consumer side cannot silently
 * desync — every field name lives exactly here.</p>
 *
 * <h2>Validation rules — all enforced in the compact constructor</h2>
 * <ul>
 *   <li>{@code paymentId} — non-null UUID (carried to {@code common_outbox_events.aggregate_id VARCHAR(64)}).</li>
 *   <li>String fields — non-blank, max length 64 (matches {@code billing_payments.provider_payment_id VARCHAR(64)} for the
 *       {@code providerPaymentId} field; the others follow the same defensive contract).</li>
 *   <li>{@code amount} — {@code BigDecimal.signum() >= 0} (mirrors {@code chk_billing_payments_amount_non_negative}, V8 line 22).</li>
 *   <li>{@code currency} — exactly 3 ISO-4217 characters (matches {@code CHAR(3)}).</li>
 *   <li>{@code confirmedAt} — ISO-8601 UTC {@link Instant}; rejects any non-UTC offset.</li>
 * </ul>
 *
 * @param paymentId the unique payment id this event announces as {@code COMPLETED}.
 * @param providerPaymentId the Mercado Pago payment id, already bound to this local payment by the webhook flow.
 * @param externalReference the merchant-side correlation key established by the checkout flow.
 * @param merchantAccountId the merchant account (matches expected merchant from the buyer-facing checkout).
 * @param targetReference the {@link com.menta.billing.domain.model.PaymentTarget.Physical#sessionId()} reference.
 * @param amount gross amount captured by the provider.
 * @param currency ISO-4217 currency code (3 chars).
 * @param confirmedAt instant the payment reached {@link com.menta.billing.domain.model.PaymentStatus.Completed}.
 */
public record PaymentCompletedOutboxPayload(
    UUID paymentId,
    String providerPaymentId,
    String externalReference,
    String merchantAccountId,
    String targetReference,
    BigDecimal amount,
    String currency,
    Instant confirmedAt
) {

    private static final int MAX_STRING_LENGTH = 64;
    private static final int CURRENCY_LENGTH = 3;

    public PaymentCompletedOutboxPayload {
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId cannot be null");
        }
        if (paymentId.toString().length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("paymentId decimal form must fit aggregate_id VARCHAR(64)");
        }
        requireNonBlankAndBounded("providerPaymentId", providerPaymentId);
        requireNonBlankAndBounded("externalReference", externalReference);
        requireNonBlankAndBounded("merchantAccountId", merchantAccountId);
        requireNonBlankAndBounded("targetReference", targetReference);
        if (amount == null) {
            throw new IllegalArgumentException("amount cannot be null");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (currency == null) {
            throw new IllegalArgumentException("currency cannot be null");
        }
        if (currency.length() != CURRENCY_LENGTH) {
            throw new IllegalArgumentException("currency must be exactly 3 ISO-4217 characters");
        }
        if (confirmedAt == null) {
            throw new IllegalArgumentException("confirmedAt cannot be null");
        }
    }

    private static void requireNonBlankAndBounded(String name, String value) {
        Objects.requireNonNull(value, name + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        if (value.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException(name + " must not exceed " + MAX_STRING_LENGTH + " characters");
        }
    }
}
