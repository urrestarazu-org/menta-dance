package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.OwnPaymentEntry;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One entry of the {@code 200} body for {@code GET /api/v1/billing/payments/me} (#767).
 *
 * <p>Amount and currency are flat, as in every other billing response. Nothing internal is
 * rendered: no provider ids, no external reference, no merchant account, no reconciliation reason
 * and nothing about a transfer proof. {@code item.name} is always serialized, {@code null} when
 * unknown.</p>
 */
public record OwnPaymentResponse(
    String paymentId, String status, BigDecimal amount, String currency, String paymentMethod, Instant createdAt,
    Item item
) {

    /** Maps one entry to the wire shape, rendering the enums by name. */
    public static OwnPaymentResponse from(OwnPaymentEntry entry) {
        OwnPaymentEntry.Item item = entry.item();
        return new OwnPaymentResponse(
            entry.paymentId(), entry.status(), entry.amount().getAmount(), entry.amount().getCurrency(),
            entry.paymentMethod().name(), entry.createdAt(), new Item(item.type().name(), item.id(), item.name())
        );
    }

    /** What the payment buys: a plan subscription or a physical course. */
    public record Item(String type, String id, String name) {
    }
}
