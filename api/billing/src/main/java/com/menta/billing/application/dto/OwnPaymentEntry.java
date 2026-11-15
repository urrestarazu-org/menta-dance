package com.menta.billing.application.dto;

import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PaymentMethod;
import java.time.Instant;

/**
 * One payment of the authenticated student's own list (#767).
 *
 * <p>A read projection, not a rehydrated {@link com.menta.billing.domain.model.Payment}: it carries
 * only what the student may see. Provider ids, the external reference, the merchant account, the
 * reconciliation reason and anything about a transfer proof are deliberately absent.</p>
 *
 * @param status the {@code status_type} vocabulary {@code PaymentJpaMapper} persists, the same one
 *     {@code GET /payments/{paymentId}} returns
 * @param item what the payment buys
 */
public record OwnPaymentEntry(
    String paymentId, String status, Money amount, PaymentMethod paymentMethod, Instant createdAt, Item item
) {

    /** What a payment buys. */
    public enum ItemType {

        /** A subscription to a virtual plan; the item id is the plan id. */
        SUBSCRIPTION,

        /** A purchase of a physical course; the item id is the course id. */
        PHYSICAL_COURSE
    }

    /**
     * The bought item.
     *
     * @param name the plan name for a subscription; {@code null} for a physical course, whose name
     *     lives in the Physical module and is not known to Billing without another query
     */
    public record Item(ItemType type, String id, String name) {
    }
}
